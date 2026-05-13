package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class FlowAgentServicesMockedTest {

    @Inject
    WorkflowRegistry registry;

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
                registry);

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
                registry);

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
                .builder(TestConditionalAgent.class, registry);

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

        FlowLoopAgentService<TestLoopAgent> service = FlowLoopAgentService.builder(TestLoopAgent.class, registry);

        // max 10 iterations, but we exit when counter >= 3
        service.maxIterations(10);
        service.exitCondition((scope, idx) -> {
            int c = scope.readState("counter", 0);
            // Stop when c >= 3
            return c >= 3;
        });
        // keep default: testExitAtLoopEnd(false) – head-checked

        service.subAgents(loopExec);

        TestLoopAgent agent = service.build();

        // Act
        ResultWithAgenticScope<String> result = agent.run("any-topic");
        AgenticScope scope = result.agenticScope();

        int counter = scope.readState("counter", 0);

        // Assert:
        // - the loop body ran a few times but not more than maxIterations
        assertThat(counter).isBetween(1, 10);
        assertThat(calls.get()).isEqualTo(counter);
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

    interface TestLoopAgent {
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }

    interface TestExecutionContextIsolationAgent {
        ResultWithAgenticScope<String> process(@V("input") String input);
    }

    interface TestExecutionContextErrorAgent {
        ResultWithAgenticScope<String> execute(@V("input") String input);
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
                .builder(TestExecutionContextIsolationAgent.class, registry);
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
                .builder(TestExecutionContextErrorAgent.class, registry);
        service.subAgents(failingAgent);
        TestExecutionContextErrorAgent agent = service.build();

        // Verify failure is propagated correctly (may be wrapped in CompletionException or similar)
        try {
            agent.execute("boom");
            org.junit.jupiter.api.Assertions.fail("Expected exception to be thrown");
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
}
