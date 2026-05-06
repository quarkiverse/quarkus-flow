package org.acme.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.BuildTask;
import org.acme.orchestrator.model.TaskExecutionContext;
import org.acme.orchestrator.model.TaskResult;
import org.acme.orchestrator.model.TaskStatus;
import org.acme.orchestrator.service.StateReconciliationService;
import org.acme.orchestrator.service.TaskExecutor;
import org.acme.orchestrator.service.TaskStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

/**
 * Task Workflow - executes individual build tasks with resume support.
 *
 * Key features:
 * 1. Idempotent execution (can safely retry/resume)
 * 2. State reconciliation before execution
 * 3. Automatic retry on failure (up to max attempts)
 * 4. Publishes completion event for coordinator
 *
 * This workflow demonstrates the resilient task pattern:
 * - Check state before executing
 * - Execute in idempotent phases
 * - Persist state after each phase
 * - Retry with backoff on failure
 */
@ApplicationScoped
public class TaskWorkflow extends Flow {
    private static final Logger LOG = LoggerFactory.getLogger(TaskWorkflow.class);

    @Inject
    StateReconciliationService reconciliationService;

    @Inject
    TaskExecutor taskExecutor;

    @Inject
    TaskStateStore stateStore;

    private static final int MAX_RETRIES = 5;

    @Override
    public Workflow descriptor() {
        return workflow("build-task")
                // 1. Listen for task start event from coordinator
                .schedule(on(one("org.acme.build.task.started")))
                .tasks(
                        // 2. Extract BuildTask from CloudEvent and reconcile state
                        function("extractAndReconcile", (BuildTask task) -> {
                            LOG.info("Reconciling state for task: {}", task.id());
                            StateReconciliationService.ReconciliationResult result = reconciliationService.reconcile(task.id());

                            if (!result.canResume()) {
                                LOG.error("Cannot resume task {}: {}", task.id(), result.message());
                                throw new IllegalStateException(
                                        "State reconciliation failed: " + result.message());
                            }

                            LOG.info("Task {} reconciliation successful: {}", task.id(), result.message());
                            return task;
                        })// Extract BuildTask from CloudEvent structure: schedule() returns array of CloudEvents
                                .inputFrom((JsonNode node) -> node.isArray() ? node.get(0).get("data") : node.get("data")),

                        // 3. Execute task (idempotent, can retry)
                        function("execute", (BuildTask task) -> {
                            LOG.info("Executing task: {} ({})", task.id(), task.name());
                            try {
                                TaskResult result = taskExecutor.executeTask(task);
                                LOG.info("Task {} completed: {}", task.id(), result.message());
                                return new TaskExecutionContext(task, result);
                            } catch (TaskExecutor.TaskExecutionException e) {
                                LOG.error("Task {} failed: {}", task.id(), e.getMessage());
                                TaskResult result = new TaskResult(task.id(), TaskStatus.FAILED, e.getMessage(), 1);
                                return new TaskExecutionContext(task, result);
                            }
                        }),

                        // 4. Check if task succeeded or needs retry
                        switchWhenOrElse(
                                (TaskExecutionContext ctx) -> ctx.result().status() == TaskStatus.COMPLETED,
                                "taskCompleted",
                                "checkRetry"),

                        // 5. Check retry limit
                        consume("checkRetry", (TaskExecutionContext ctx) -> {
                            if (ctx.result().attemptNumber() >= MAX_RETRIES) {
                                LOG.error("Task {} exhausted retries ({}/{}), giving up",
                                        ctx.result().taskId(), ctx.result().attemptNumber(), MAX_RETRIES);
                                throw new RuntimeException(
                                        "Task failed after " + MAX_RETRIES + " attempts");
                            }
                            LOG.info("Task {} failed, will retry (attempt {}/{})",
                                    ctx.result().taskId(), ctx.result().attemptNumber(), MAX_RETRIES);
                        }).then("retryExecute"),

                        // 6. Retry execution - reconcile and execute again
                        function("retryExecute", (TaskExecutionContext ctx) -> {
                            BuildTask task = ctx.task();

                            // Reconcile before retry
                            LOG.info("Reconciling state before retry for task: {}", task.id());
                            StateReconciliationService.ReconciliationResult reconcileResult = reconciliationService
                                    .reconcile(task.id());

                            if (!reconcileResult.canResume()) {
                                LOG.error("Cannot retry task {}: {}", task.id(), reconcileResult.message());
                                TaskResult result = new TaskResult(task.id(), TaskStatus.FAILED,
                                        "Reconciliation failed: " + reconcileResult.message(),
                                        stateStore.get(task.id()).getAttemptCount());
                                return new TaskExecutionContext(task, result);
                            }

                            // Execute task
                            try {
                                TaskResult result = taskExecutor.executeTask(task);
                                LOG.info("Retry execution for task {}: {}", task.id(), result.message());
                                return new TaskExecutionContext(task, result);
                            } catch (TaskExecutor.TaskExecutionException e) {
                                LOG.error("Retry failed for task {}: {}", task.id(), e.getMessage());
                                TaskResult result = new TaskResult(task.id(), TaskStatus.FAILED,
                                        e.getMessage(), stateStore.get(task.id()).getAttemptCount());
                                return new TaskExecutionContext(task, result);
                            }
                        }).then("switch-2"), // Jump back to status check

                        // 7. Task completed successfully - log and emit completion event
                        consume("taskCompleted", (TaskExecutionContext ctx) -> {
                            LOG.info("Task {} completed successfully after {} attempt(s)",
                                    ctx.result().taskId(), ctx.result().attemptNumber());
                        }),

                        // 8. Extract TaskResult for emission
                        function("extractResult", TaskExecutionContext::result),

                        // 9. Emit completion event
                        emitJson("emitCompletion", "org.acme.build.task.completed", TaskResult.class)
                                .then(FlowDirectiveEnum.END))
                .build();
    }
}
