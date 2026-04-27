package org.acme.orchestrator.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.BuildTask;
import org.acme.orchestrator.model.TaskResult;
import org.acme.orchestrator.model.TaskStatus;
import org.acme.orchestrator.service.StateReconciliationService;
import org.acme.orchestrator.service.TaskExecutor;
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

    private static final int MAX_RETRIES = 3;

    @Override
    public Workflow descriptor() {
        return workflow("build-task")
                .tasks(
                        // 1. Listen for task start event from coordinator
                        listen("awaitTaskStart", toOne("org.acme.build.task.started"))
                                .outputAs((JsonNode node) -> node.isArray() ? node.get(0) : node),

                        // 2. Reconcile state before execution
                        function("reconcile", (BuildTask task) -> {
                            LOG.info("Reconciling state for task: {}", task.id());
                            StateReconciliationService.ReconciliationResult result =
                                    reconciliationService.reconcile(task.id());

                            if (!result.canResume()) {
                                LOG.error("Cannot resume task {}: {}", task.id(), result.message());
                                throw new IllegalStateException(
                                        "State reconciliation failed: " + result.message());
                            }

                            LOG.info("Task {} reconciliation successful: {}", task.id(), result.message());
                            return task;
                        }, BuildTask.class),

                        // 3. Execute task (idempotent, can retry)
                        function("execute", (BuildTask task) -> {
                            LOG.info("Executing task: {} ({})", task.id(), task.name());
                            try {
                                TaskResult result = taskExecutor.executeTask(task);
                                LOG.info("Task {} completed: {}", task.id(), result.message());
                                return result;
                            } catch (TaskExecutor.TaskExecutionException e) {
                                LOG.error("Task {} failed: {}", task.id(), e.getMessage());
                                return new TaskResult(task.id(), TaskStatus.FAILED, e.getMessage(), 1);
                            }
                        }, BuildTask.class),

                        // 4. Check if task succeeded or needs retry
                        switchWhenOrElse(
                                (TaskResult result) -> result.status() == TaskStatus.COMPLETED,
                                "taskCompleted",
                                "checkRetry",
                                TaskResult.class),

                        // 5. Retry logic
                        consume("checkRetry", (TaskResult result) -> {
                            if (result.attemptNumber() >= MAX_RETRIES) {
                                LOG.error("Task {} exhausted retries ({}/{}), giving up",
                                        result.taskId(), result.attemptNumber(), MAX_RETRIES);
                                throw new RuntimeException(
                                        "Task failed after " + MAX_RETRIES + " attempts");
                            }
                            LOG.info("Task {} failed, will retry (attempt {}/{})",
                                    result.taskId(), result.attemptNumber(), MAX_RETRIES);
                        }, TaskResult.class)
                                .then("reconcile"), // Jump back to reconcile step

                        // 6. Task completed successfully - publish completion event
                        consume("taskCompleted", (TaskResult result) -> {
                            LOG.info("Task {} completed successfully after {} attempt(s)",
                                    result.taskId(), result.attemptNumber());
                        }, TaskResult.class),

                        emitJson("taskCompleted", "org.acme.build.task.completed", TaskResult.class)
                                .then(FlowDirectiveEnum.END))
                .build();
    }
}
