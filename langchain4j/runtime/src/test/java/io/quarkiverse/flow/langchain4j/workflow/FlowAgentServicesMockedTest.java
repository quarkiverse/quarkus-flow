package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeLoopAgenticFlow;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeWorkflowApplicationProvider;
import io.quarkiverse.flow.langchain4j.workflow.service.FlowConditionalAgentService;
import io.quarkiverse.flow.langchain4j.workflow.service.FlowLoopAgentService;
import io.quarkiverse.flow.langchain4j.workflow.service.FlowParallelAgentService;
import io.quarkiverse.flow.langchain4j.workflow.service.FlowSequentialAgentService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class FlowAgentServicesMockedTest {

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    @Test
    void sequentialAgentInvokesExecutorsInOrder() {
        var agent1 = AgenticServices.agentAction(scope -> {
            StringBuilder sb = scope.readState("seqOrder", new StringBuilder());
            sb.append("1");
            scope.writeState("seqOrder", sb);
        });

        var agent2 = AgenticServices.agentAction(scope -> {
            StringBuilder sb = scope.readState("seqOrder", new StringBuilder());
            sb.append("2");
            scope.writeState("seqOrder", sb);
        });

        // Build our Flow-backed LC4J service
        FlowSequentialAgentService<TestSequentialAgent> service = FlowSequentialAgentService.builder(TestSequentialAgent.class,
                runtimeAppProvider);

        // Register sub-agents (executors)
        service.subAgents(agent1, agent2);

        TestSequentialAgent agent = service.build();

        // Act
        ResultWithAgenticScope<String> result = agent.run("hello");
        AgenticScope scope = result.agenticScope();

        // And the custom state shows order "12"
        StringBuilder seqOrder = scope.readState("seqOrder", new StringBuilder());
        assertThat(seqOrder.toString()).isEqualTo("12");
    }

    @Test
    void parallelAgentInvokesAllBranches() {
        var agent1 = AgenticServices.agentAction(scope -> scope.writeState("calledA", true));
        var agent2 = AgenticServices.agentAction(scope -> scope.writeState("calledB", true));
        var agent3 = AgenticServices.agentAction(scope -> scope.writeState("calledC", true));

        FlowParallelAgentService<TestParallelAgent> service = FlowParallelAgentService.builder(TestParallelAgent.class,
                runtimeAppProvider);

        service.subAgents(agent1, agent2, agent3);

        TestParallelAgent agent = service.build();

        // Act
        ResultWithAgenticScope<String> result = agent.run("parallel-input");
        AgenticScope scope = result.agenticScope();

        assertThat(scope.readState("calledA", false)).isTrue();
        assertThat(scope.readState("calledB", false)).isTrue();
        assertThat(scope.readState("calledC", false)).isTrue();
    }

    @Test
    void conditionalAgentInvokesOnlyMatchingBranch() {
        var medicalExec = AgenticServices.agentAction(scope -> scope.writeState("branch", "medical"));
        var financeExec = AgenticServices.agentAction(scope -> scope.writeState("branch", "finance"));

        FlowConditionalAgentService<TestConditionalAgent> service = FlowConditionalAgentService
                .builder(TestConditionalAgent.class, runtimeAppProvider);

        // condition on state("type")
        Predicate<AgenticScope> isMedical = scope -> "medical".equals(scope.readState("type", ""));
        Predicate<AgenticScope> isFinance = scope -> "finance".equals(scope.readState("type", ""));

        service.subAgents(isMedical, medicalExec);
        service.subAgents(isFinance, financeExec);

        TestConditionalAgent agent = service.build();

        // Act: route("medical")
        ResultWithAgenticScope<String> result = agent.route("medical");
        AgenticScope scope = result.agenticScope();

        assertThat(scope.readState("branch", "")).isEqualTo("medical");
    }

    @Test
    void loopAgentHonorsExitConditionAndMaxIterations() {
        AtomicInteger calls = new AtomicInteger();

        // Each execution increments "counter" in the scope
        var loopExec = AgenticServices.agentAction(scope -> {
            int c = scope.readState("counter", 0);
            c++;
            scope.writeState("counter", c);
            calls.incrementAndGet();
        });

        FlowLoopAgentService<TestLoopAgent> service = FlowLoopAgentService.builder(TestLoopAgent.class, runtimeAppProvider);

        // max 10 iterations, but we exit when counter >= 3
        service.maxIterations(10);
        service.exitCondition((scope, idx) -> {
            int c = scope.readState("counter", 0);
            // Stop when c >= 3
            return c >= 3;
        });
        // Check after each subagent.

        service.subAgents(loopExec);

        TestLoopAgent agent = service.build();

        // Act
        ResultWithAgenticScope<String> result = agent.run("any-topic");
        AgenticScope scope = result.agenticScope();

        int counter = scope.readState("counter", 0);

        // Assert:
        // - the loop body ran a few times but not more than maxIterations
        assertThat(counter).isEqualTo(3);
        assertThat(calls.get()).isEqualTo(counter);
    }

    @Test
    @DisplayName("while_loop_checks_generated_predicate_after_first_subagent")
    void while_loop_checks_generated_predicate_after_first_subagent() {
        AtomicInteger bodyRuns = new AtomicInteger();
        var evaluator = AgenticServices.agentAction(scope -> {
            scope.writeState("score", 0.9);
            bodyRuns.incrementAndGet();
        });
        RuntimeLoopAgenticFlow runtimeFlow = new RuntimeLoopAgenticFlow(
                ScoreExitConditionHolder.class.getName(), runtimeAppProvider);
        BiPredicate<AgenticScope, Integer> predicate = runtimeFlow.buildLoopExitPredicate(
                ScoreExitConditionHolder.class, "exit", List.of(Double.class.getName()));

        var service = FlowLoopAgentService.builder(TestLoopAgent.class, runtimeAppProvider);
        service.maxIterations(10);
        service.exitCondition(predicate);
        service.subAgents(evaluator);

        var result = service.build().run("topic");

        assertThat(result.agenticScope().readState("score", (Double) null)).isEqualTo(0.9);
        assertThat(bodyRuns.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @DisplayName("loop_checks_exit_at_configured_boundary_once")
    void loop_checks_exit_at_configured_boundary_once(boolean testAtEnd) {
        AtomicInteger evaluatorRuns = new AtomicInteger();
        AtomicInteger reviserRuns = new AtomicInteger();
        AtomicInteger predicateCalls = new AtomicInteger();
        AtomicInteger evaluatorRunsAtExitCheck = new AtomicInteger(-1);
        var evaluator = AgenticServices.agentAction(scope -> evaluatorRuns.incrementAndGet());
        var reviser = AgenticServices.agentAction(scope -> reviserRuns.incrementAndGet());
        var service = FlowLoopAgentService.builder(TestLoopAgent.class, runtimeAppProvider);
        service.maxIterations(10);
        service.testExitAtLoopEnd(testAtEnd);
        service.exitCondition((scope, idx) -> {
            evaluatorRunsAtExitCheck.set(evaluatorRuns.get());
            return predicateCalls.incrementAndGet() == 1;
        });
        service.subAgents(evaluator, reviser);

        service.build().run("topic");

        assertThat(evaluatorRunsAtExitCheck.get()).isEqualTo(1);
        assertThat(evaluatorRuns.get()).isEqualTo(1);
        assertThat(reviserRuns.get()).isEqualTo(testAtEnd ? 1 : 0);
        assertThat(predicateCalls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @DisplayName("nested_loop_exit_does_not_stop_parent_and_resets_on_reentry")
    void nested_loop_exit_does_not_stop_parent_and_resets_on_reentry(boolean innerTestAtEnd) {
        AtomicInteger innerRuns = new AtomicInteger();
        AtomicInteger outerRuns = new AtomicInteger();
        var inner = FlowLoopAgentService.builder(TestLoopAgent.class, runtimeAppProvider);
        inner.maxIterations(5);
        inner.testExitAtLoopEnd(innerTestAtEnd);
        inner.exitCondition((scope, idx) -> true);
        inner.subAgents(AgenticServices.agentAction(scope -> innerRuns.incrementAndGet()));

        var outer = FlowLoopAgentService.builder(TestLoopAgent.class, runtimeAppProvider);
        outer.maxIterations(3);
        outer.exitCondition((scope, idx) -> false);
        outer.subAgents(inner.build(), AgenticServices.agentAction(scope -> outerRuns.incrementAndGet()));

        outer.build().run("topic");

        assertThat(innerRuns.get()).isEqualTo(3);
        assertThat(outerRuns.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("sibling_loops_each_execute_on_shared_scope")
    void sibling_loops_each_execute_on_shared_scope() {
        AtomicInteger firstRuns = new AtomicInteger();
        AtomicInteger secondRuns = new AtomicInteger();
        var first = FlowLoopAgentService.builder(TestLoopAgent.class, runtimeAppProvider);
        first.maxIterations(5);
        first.exitCondition((scope, idx) -> true);
        first.subAgents(AgenticServices.agentAction(scope -> firstRuns.incrementAndGet()));
        var second = FlowLoopAgentService.builder(TestInnerLoopAgent.class, runtimeAppProvider);
        second.maxIterations(5);
        second.exitCondition((scope, idx) -> true);
        second.subAgents(AgenticServices.agentAction(scope -> secondRuns.incrementAndGet()));
        var sequence = FlowSequentialAgentService.builder(TestSequentialAgent.class, runtimeAppProvider);
        sequence.subAgents(first.build(), second.build());

        sequence.build().run("topic");

        assertThat(firstRuns.get()).isEqualTo(1);
        assertThat(secondRuns.get()).isEqualTo(1);
    }

    @Test
    void executionContext_isolates_workflow_instances() {
        // Test that multiple sequential workflow executions each have isolated executionContext
        var agent = AgenticServices.agentAction(scope -> {
            // Each workflow should have its own FlowPlanner in executionContext
            // Store a unique marker in the state to verify isolation
            String input = scope.readState("input", "");
            scope.writeState("processed", input);
        });

        FlowSequentialAgentService<TestExecutionContextIsolationAgent> service = FlowSequentialAgentService
                .builder(TestExecutionContextIsolationAgent.class, runtimeAppProvider);
        service.subAgents(agent);
        TestExecutionContextIsolationAgent testAgent = service.build();

        // Run multiple workflows sequentially and verify each has isolated context
        ResultWithAgenticScope<String> result1 = testAgent.process("workflow-1");
        assertThat(result1.agenticScope().readState("processed", "")).isEqualTo("workflow-1");

        ResultWithAgenticScope<String> result2 = testAgent.process("workflow-2");
        assertThat(result2.agenticScope().readState("processed", "")).isEqualTo("workflow-2");

        // Verify first workflow's state wasn't affected by second workflow
        assertThat(result1.agenticScope().readState("processed", "")).isEqualTo("workflow-1");
    }

    @Test
    void executionContext_accessible_during_error_handling() {
        // Test that executionContext remains accessible even when agents throw exceptions
        var failingAgent = AgenticServices.agentAction(scope -> {
            String input = scope.readState("input", "");
            if (input.startsWith("boom")) {
                throw new RuntimeException("Intentional test failure");
            }
            scope.writeState("success", true);
        });

        FlowSequentialAgentService<TestExecutionContextErrorAgent> service = FlowSequentialAgentService
                .builder(TestExecutionContextErrorAgent.class, runtimeAppProvider);
        service.subAgents(failingAgent);
        TestExecutionContextErrorAgent agent = service.build();

        // Verify failure is propagated correctly (may be wrapped in CompletionException or similar)
        try {
            agent.execute("boom");
            Assertions.fail("Expected exception to be thrown");
        } catch (Exception e) {
            // Expected - verify exception was thrown (may be wrapped)
            Throwable rootCause = e;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            assertThat(rootCause)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Intentional test failure");
        }

        // Verify successful execution still works after error
        ResultWithAgenticScope<String> result = agent.execute("success");
        assertThat(result.agenticScope().readState("success", false)).isTrue();
    }

    interface TestSequentialAgent {
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }

    interface TestParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }

    interface TestConditionalAgent {
        ResultWithAgenticScope<String> route(@V("type") String type);
    }

    public interface TestLoopAgent {
        @Agent
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }

    public interface TestInnerLoopAgent {
        @Agent
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }

    interface TestExecutionContextIsolationAgent {
        ResultWithAgenticScope<String> process(@V("input") String input);
    }

    interface TestExecutionContextErrorAgent {
        ResultWithAgenticScope<String> execute(@V("input") String input);
    }
}
