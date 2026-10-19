package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeWorkflowApplicationProvider;
import io.quarkiverse.flow.langchain4j.workflow.service.FlowParallelAgentService;
import io.quarkiverse.flow.langchain4j.workflow.service.FlowSequentialAgentService;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

/**
 * When an agent of a Flow-backed agentic system fails, the workflows generated for it end, and no thread stays blocked
 * waiting for an agent call that will never complete.
 */
@QuarkusTest
class FlowPlannerAgentFailureTest {

    // Where a generated workflow's task waits for its agent call: FlowPlanner.executeAgent(i).join()
    private static final String AGENT_CALL_CLASS = "io.quarkiverse.flow.langchain4j.workflow.flow.AgenticFlow";
    private static final String AGENT_CALL_METHOD = "executeAgent";
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    private Set<Thread> blockedBefore;

    @BeforeEach
    void reset() {
        RunRecorder.reset();
        blockedBefore = threadsBlockedInAnAgentCall();
    }

    @Test
    void failingAgentInSequenceEndsTheWorkflow() {
        UntypedAgent agent = sequence("root", succeeding(), failing());

        assertThatThrownBy(() -> agent.invoke(Map.of("topic", "test")))
                .hasRootCauseMessage("boom");

        assertEveryRunEndedAndNoThreadIsBlocked(1);
        assertThat(RunRecorder.failed).hasSize(1);
    }

    @Test
    void failingAgentInNestedSequenceEndsBothWorkflows() {
        UntypedAgent agent = sequence("root", succeeding(), sequence("inner", failing()));

        assertThatThrownBy(() -> agent.invoke(Map.of("topic", "test")))
                .hasRootCauseMessage("boom");

        assertEveryRunEndedAndNoThreadIsBlocked(2);
        assertThat(RunRecorder.failed).hasSize(2);
    }

    @Test
    void failedNestedSequenceRecoveredByTheParentEndsTheNestedWorkflow() {
        FlowSequentialAgentService<UntypedAgent> root = FlowSequentialAgentService.builder(runtimeAppProvider);
        root.name("root")
                .subAgents(sequence("inner", failing()), succeeding())
                .errorHandler(context -> "inner".equals(context.agentName())
                        ? ErrorRecoveryResult.result("recovered")
                        : ErrorRecoveryResult.throwException());
        UntypedAgent agent = root.build();

        agent.invoke(Map.of("topic", "test"));

        assertEveryRunEndedAndNoThreadIsBlocked(2);
        assertThat(RunRecorder.failed)
                .as("the nested workflow failed, the root one completed")
                .hasSize(1);
        assertThat(RunRecorder.completed).hasSize(1);
    }

    @Test
    void failingBranchOfParallelEndsTheWorkflow() {
        FlowParallelAgentService<UntypedAgent> parallel = FlowParallelAgentService.builder(runtimeAppProvider);
        parallel.name("root").subAgents(succeeding(), failing(), succeeding());
        UntypedAgent agent = parallel.build();

        assertThatThrownBy(() -> agent.invoke(Map.of("topic", "test")))
                .hasRootCauseMessage("boom");

        assertEveryRunEndedAndNoThreadIsBlocked(1);
        assertThat(RunRecorder.failed).hasSize(1);
    }

    private UntypedAgent sequence(String name, Object... subAgents) {
        FlowSequentialAgentService<UntypedAgent> service = FlowSequentialAgentService.builder(runtimeAppProvider);
        service.name(name).subAgents(subAgents);
        return service.build();
    }

    private static Object succeeding() {
        return AgenticServices.agentAction(scope -> scope.writeState("step", "done"));
    }

    private static Object failing() {
        return AgenticServices.agentAction(scope -> {
            throw new IllegalStateException("boom");
        });
    }

    private void assertEveryRunEndedAndNoThreadIsBlocked(int expectedRuns) {
        Awaitility.await().atMost(WAIT).untilAsserted(() -> {
            assertThat(RunRecorder.started).hasSize(expectedRuns);
            assertThat(RunRecorder.ended())
                    .as("every generated workflow ended")
                    .containsExactlyInAnyOrderElementsOf(RunRecorder.started);
            assertThat(threadsBlockedInAnAgentCall())
                    .as("no thread stays blocked waiting for an agent call that will never complete")
                    .isSubsetOf(blockedBefore);
        });
    }

    private static Set<Thread> threadsBlockedInAnAgentCall() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(thread -> Arrays.stream(thread.getValue())
                        .anyMatch(frame -> AGENT_CALL_CLASS.equals(frame.getClassName())
                                && AGENT_CALL_METHOD.equals(frame.getMethodName())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @ApplicationScoped
    static class RunRecorder implements WorkflowExecutionListener {

        static final Set<String> started = ConcurrentHashMap.newKeySet();
        static final Set<String> completed = ConcurrentHashMap.newKeySet();
        static final Set<String> failed = ConcurrentHashMap.newKeySet();
        static final Set<String> cancelled = ConcurrentHashMap.newKeySet();

        static void reset() {
            started.clear();
            completed.clear();
            failed.clear();
            cancelled.clear();
        }

        static Set<String> ended() {
            Set<String> ended = ConcurrentHashMap.newKeySet();
            ended.addAll(completed);
            ended.addAll(failed);
            ended.addAll(cancelled);
            return ended;
        }

        @Override
        public void onWorkflowStarted(WorkflowStartedEvent event) {
            started.add(event.workflowContext().instanceData().id());
        }

        @Override
        public void onWorkflowCompleted(WorkflowCompletedEvent event) {
            completed.add(event.workflowContext().instanceData().id());
        }

        @Override
        public void onWorkflowFailed(WorkflowFailedEvent event) {
            failed.add(event.workflowContext().instanceData().id());
        }

        @Override
        public void onWorkflowCancelled(WorkflowCancelledEvent event) {
            cancelled.add(event.workflowContext().instanceData().id());
        }
    }
}
