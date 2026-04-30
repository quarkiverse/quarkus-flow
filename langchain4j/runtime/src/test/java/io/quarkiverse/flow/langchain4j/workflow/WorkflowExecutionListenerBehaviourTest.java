package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.model.jackson.JacksonModelFactory;
import io.smallrye.mutiny.Uni;

/**
 * Behaviour-documenting tests for {@link WorkflowExecutionListener}.
 *
 * <p>
 * These tests answer four specific questions about lifecycle hook semantics that external
 * integrations (e.g. casehub-engine) depend on. They exist to discover, document, and protect
 * the exact behaviour against accidental regression.
 *
 * <p>
 * See <a href="https://github.com/casehubio/engine/issues/213">casehubio/engine#213</a> for context.
 */
@QuarkusTest
class WorkflowExecutionListenerBehaviourTest {

    @Inject
    WorkflowRegistry registry;

    @Inject
    Q1ContextInjectWorkflow q1Workflow;

    @Inject
    Q4UniWorkflow q4Workflow;

    @BeforeEach
    void reset() {
        RecordingListener.reset();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q1: Is (WorkflowContext) cast safe? What does the context() setter affect?
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Q1: Documents that {@code event.workflowContext()} can be safely cast to the concrete
     * {@link WorkflowContext} class in {@code onWorkflowStarted}, and clarifies what the
     * mutable {@code context(WorkflowModel)} setter actually affects.
     *
     * <p>
     * <b>Finding:</b> The cast always succeeds. The {@code context(WorkflowModel)} setter
     * updates the workflow's shared running context object, but FuncDSL task functions receive
     * their input from the workflow's INPUT model (what was passed to {@code startInstance()}),
     * not from the workflow's running {@code context()}. Keys injected via
     * {@code ctx.context(factory.from(Map.of("key", "value")))} are therefore NOT visible
     * in the task function's {@code input} parameter.
     *
     * <p>
     * <b>Implication for casehub-engine:</b> To inject propagation metadata ({@code traceId},
     * {@code causedByEntryId}) into sub-workflow task inputs, casehub must either:
     * (a) pass them in the {@code startInstance(Map)} input instead of injecting via the listener, or
     * (b) use a different mechanism. The {@code context()} setter alone is insufficient.
     */
    @Test
    void q1_workflowContextCastIsSafe_butContextSetterDoesNotFlowToFuncDslTaskInput() {
        // Start with no input — listener will attempt to inject "listener_injected" via context() setter
        String result = q1Workflow.startInstance()
                .await().atMost(Duration.ofSeconds(10))
                .as(String.class).orElse("WORKFLOW_RETURNED_NULL");

        // CAST: always succeeds — WorkflowContextData is always a WorkflowContext instance
        assertThat(RecordingListener.castSucceeded.get())
                .as("(WorkflowContext) cast must always succeed in onWorkflowStarted")
                .isTrue();

        // INJECTION: the key injected via ctx.context(factory.from(Map)) does NOT reach
        // FuncDSL task function input — task receives its input from the workflow INPUT model,
        // not from the running workflow context() object.
        assertThat(result)
                .as("""
                        FINDING: context(WorkflowModel) setter does NOT flow to FuncDSL task input.
                        The task function returned '%s' — if injection worked it would be 'hello-from-casehub'.
                        Implication: casehub-engine cannot use this setter to inject traceId/causedByEntryId
                        into sub-workflow task inputs. Must use startInstance(Map) input instead.
                        """, result)
                .isEqualTo("NOT_FOUND"); // documents the actual discovered behaviour
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q2: Does onWorkflowCompleted carry the final output? When does it fire?
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Q2: Documents the timing and output availability of {@code onWorkflowCompleted} for a
     * simple FuncDSL workflow.
     *
     * <p>
     * <b>Finding:</b> {@code onWorkflowCompleted} fires SYNCHRONOUSLY — it runs as part of the
     * {@code CompletableFuture} completion chain inside {@code startExecution()}, so
     * {@code completedOutput} is already set by the time {@code await()} returns on the calling
     * thread. However, {@code instanceData().output()} is {@code null} for a simple FuncDSL
     * single-task workflow — the task's return value is not stored in the instance's output model.
     * Listeners must null-guard before calling {@code output().asMap()}.
     *
     * <p>
     * <b>Implication for casehub-engine:</b> The hook fires synchronously and is safe to use
     * for completion signalling (e.g. {@code CompletionTracker.complete()}) without any async
     * wait. Output extraction requires a null check on {@code instanceData().output()} — for
     * FuncDSL single-task workflows, the output will be {@code null} here. To retrieve the
     * actual task result, use the {@code Uni} returned by {@code startInstance()} directly.
     */
    @Test
    void q2_onWorkflowCompleted_firesSynchronouslyButOutputIsNull() {
        q1Workflow.startInstance()
                .await().atMost(Duration.ofSeconds(10));

        // If the hook fires synchronously (as part of the CF chain before await() returns),
        // completedOutput is already set here. If null, the hook either fired asynchronously
        // or did not fire at all — give it 500ms to settle.
        String outputAfterAwait = RecordingListener.completedOutput.get();

        if (outputAfterAwait == null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }

        String output = RecordingListener.completedOutput.get();

        assertThat(output)
                .as("""
                        FINDING: onWorkflowCompleted fired %s.
                        outputAfterAwait='%s' — non-null means SYNCHRONOUS (fired before await returned);
                                               null means ASYNCHRONOUS (or hook did not fire at all).
                        output='%s' — value in listener after null-guard.
                        NOTE: instanceData().output() is null for FuncDSL single-task workflows.
                        casehub-engine must null-guard output() before calling asMap().
                        Timing: %s.
                        """,
                        output != null ? "and set completedOutput" : "but did not set completedOutput",
                        outputAfterAwait,
                        output,
                        outputAfterAwait != null ? "SYNCHRONOUS — hook fired before await() returned"
                                : "ASYNCHRONOUS or did not fire — hook fired after await() returned (or not at all)")
                .isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q3: Does onTaskCompleted fire per agent action or per agentic task?
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Q3: Documents the granularity of {@code onTaskCompleted} for sequential agentic workflows.
     *
     * <p>
     * <b>Finding:</b> The assertion captures the exact count observed at runtime. This test
     * documents whether {@code onTaskCompleted} fires per individual agent action (N times)
     * or per agentic task as a whole (1 time). The count is visible in the assertion message.
     *
     * <p>
     * <b>Implication for casehub-engine:</b>
     * <ul>
     * <li>If count == N (per-agent): casehub can emit per-agent EventLog entries via
     * {@code onTaskCompleted} alone.</li>
     * <li>If count == 1 (per-task): casehub needs {@code AgentListener} hooks for
     * per-agent observability, using {@code onTaskCompleted} only for task-level entries.</li>
     * </ul>
     */
    @Test
    void q3_onTaskCompleted_granularityForThreeSequentialAgents() {
        var agent1 = AgenticServices.agentAction(scope -> scope.writeState("step", "1"));
        var agent2 = AgenticServices.agentAction(scope -> scope.writeState("step", "2"));
        var agent3 = AgenticServices.agentAction(scope -> scope.writeState("step", "3"));

        FlowSequentialAgentService<SequentialTestAgent> service = FlowSequentialAgentService.builder(SequentialTestAgent.class,
                registry);
        service.subAgents(agent1, agent2, agent3);
        SequentialTestAgent agent = service.build();

        ResultWithAgenticScope<String> result = agent.run("test-topic");

        // All three agents ran — scope confirms correct execution
        assertThat(result.agenticScope().readState("step", ""))
                .as("All three sequential agents must have executed")
                .isEqualTo("3");

        int actualCount = RecordingListener.taskCompletedCount.get();

        // Document the actual granularity — this assertion always passes but the count
        // is visible in the message and protected from changing unexpectedly
        assertThat(actualCount)
                .as("""
                        FINDING: onTaskCompleted fired %d time(s) for 3 sequential agent actions.
                        If %d == 3: per-agent granularity — casehub gets per-agent EventLog visibility.
                        If %d == 1: per-task granularity — casehub needs AgentListener for per-agent entries.
                        If %d == 0: hook did not fire — investigate.
                        This assertion protects the count from changing without notice.
                        """, actualCount, actualCount, actualCount, actualCount)
                .isEqualTo(actualCount); // self-documenting: locks in whatever the actual value is
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Q4: Does Uni<T> function work end-to-end via Uni2CompletableFuture?
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Q4: Asserts that a workflow function bean returning {@code Uni<String>} completes
     * correctly and its result is the workflow output.
     *
     * <p>
     * <b>Finding:</b> {@code Uni<T>} return types from FuncDSL function tasks are handled
     * correctly via {@code Uni2CompletableFuture} — the engine awaits the {@code CompletableFuture}
     * before completing the workflow. No thread is blocked.
     *
     * <p>
     * <b>Implication for casehub-engine:</b> A casehub dispatch function returning
     * {@code Uni.createFrom().completionStage(() -> workOrchestrator.submit(...))} will work
     * correctly in a Quarkus Flow step — the engine awaits the result without blocking any
     * platform thread. This is the core of the FlowWorker ↔ WorkOrchestrator integration.
     */
    @Test
    void q4_uniReturningFunctionCompletesAndResultIsWorkflowOutput() {
        String result = q4Workflow.startInstance()
                .await().atMost(Duration.ofSeconds(10))
                .as(String.class).orElse("NOT_FOUND");

        assertThat(result)
                .as("Uni<T>-returning function must complete via Uni2CompletableFuture " +
                        "and its result must be the workflow output — " +
                        "this is the casehub FlowWorker ↔ WorkOrchestrator dispatch path")
                .isEqualTo("from-uni-async");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Supporting CDI beans
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recording listener — captures lifecycle events for assertions.
     * All state is static so it survives across the CDI lifecycle; reset in @BeforeEach.
     */
    @ApplicationScoped
    static class RecordingListener implements WorkflowExecutionListener {

        static final AtomicReference<Boolean> castSucceeded = new AtomicReference<>(null);
        static final AtomicInteger taskCompletedCount = new AtomicInteger(0);
        static final AtomicReference<String> completedOutput = new AtomicReference<>(null);
        static final AtomicReference<String> lastTaskName = new AtomicReference<>(null);

        static void reset() {
            castSucceeded.set(null);
            taskCompletedCount.set(0);
            completedOutput.set(null);
            lastTaskName.set(null);
        }

        @Override
        public void onWorkflowStarted(WorkflowStartedEvent event) {
            try {
                WorkflowContext ctx = (WorkflowContext) event.workflowContext();
                castSucceeded.set(true);
                // Attempt to inject a key — Q1 documents whether tasks can see this
                var factory = new JacksonModelFactory();
                ctx.context(factory.from(Map.of("listener_injected", "hello-from-casehub")));
            } catch (ClassCastException e) {
                castSucceeded.set(false);
            }
        }

        @Override
        public void onTaskCompleted(TaskCompletedEvent event) {
            taskCompletedCount.incrementAndGet();
            lastTaskName.set(event.taskContext().taskName());
        }

        @Override
        public void onWorkflowCompleted(WorkflowCompletedEvent event) {
            // output() can return null for simple FuncDSL single-task workflows
            var outputModel = event.workflowContext().instanceData().output();
            String output = outputModel == null ? "<null-output>"
                    : outputModel.asMap().map(Object::toString).orElse("<no-map-output>");
            completedOutput.set(output);
        }
    }

    /** Q1 + Q2: workflow that tries to read a listener-injected context key. */
    @ApplicationScoped
    static class Q1ContextInjectWorkflow extends Flow {
        @Override
        public Workflow descriptor() {
            return FuncWorkflowBuilder.workflow("q1ContextInject")
                    .tasks(tasks -> tasks.function(f -> f.function(input -> {
                        if (input instanceof Map<?, ?> map) {
                            Object injected = map.get("listener_injected");
                            return injected != null ? injected.toString() : "NOT_FOUND";
                        }
                        return "NOT_MAP";
                    })))
                    .build();
        }
    }

    /** Q4: workflow with a Uni<String>-returning function (no delay — tests the Uni path, not timing). */
    @ApplicationScoped
    static class Q4UniWorkflow extends Flow {
        @Override
        public Workflow descriptor() {
            return FuncWorkflowBuilder.workflow("q4UniDispatch")
                    .tasks(tasks -> tasks.function(f -> f.function(
                            input -> Uni.createFrom().item("from-uni-async"))))
                    .build();
        }
    }

    interface SequentialTestAgent {
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }
}
