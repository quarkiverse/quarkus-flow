package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import io.quarkiverse.flow.langchain4j.workflow.runtime.RuntimeWorkflowApplicationProvider;
import io.quarkiverse.flow.langchain4j.workflow.service.FlowSequentialAgentService;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Regression test for the Collection callback duplication bug.
 * <p>
 * Bug: When calling {@code service.subAgents(agent1, agent2)}, the parent LangChain4j class
 * converts varargs to Collection and calls our Collection override as a callback. If the
 * Collection override also adds task names, we get duplicate task registrations, resulting
 * in workflows with 2N tasks for N agents.
 * <p>
 * Symptom: ERROR logs showing "Invalid subagent index: N. Available subagents: N" when
 * trying to execute the (N+1)th task that shouldn't exist.
 * <p>
 * Fix: Only add task names in the varargs override. The Collection override must ONLY
 * delegate to parent without adding task names.
 * <p>
 * This test verifies that registering N agents results in exactly N workflow tasks,
 * not 2N tasks.
 */
@QuarkusTest
@DisplayName("Collection callback duplication regression test")
public class CollectionCallbackDuplicationRegressionTest {

    @Inject
    RuntimeWorkflowApplicationProvider runtimeAppProvider;

    @Test
    @DisplayName("sequential_agent_with_3_agents_creates_exactly_3_tasks")
    void sequential_agent_with_3_agents_creates_exactly_3_tasks() {
        // Track how many agents actually executed
        AtomicInteger executionCount = new AtomicInteger(0);

        var agent1 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("step1", true);
        });

        var agent2 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("step2", true);
        });

        var agent3 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("step3", true);
        });

        FlowSequentialAgentService<TestSequentialAgent> service = FlowSequentialAgentService.builder(
                TestSequentialAgent.class, runtimeAppProvider);

        // This triggers the varargs → Collection callback chain
        service.subAgents(agent1, agent2, agent3);

        TestSequentialAgent agent = service.build();

        // Execute the workflow
        ResultWithAgenticScope<String> result = agent.run("test");

        // REGRESSION CHECK: All 3 agents must execute exactly once
        // If the duplication bug returns, we'd see fewer than 3 executions
        // (workflow would fail with "Invalid subagent index" trying to access non-existent 4th, 5th, 6th tasks)
        assertThat(executionCount.get())
                .as("REGRESSION: Collection callback must not duplicate task registrations. " +
                        "Expected 3 agents to execute exactly once each. " +
                        "If < 3, the duplication bug has returned and created phantom tasks.")
                .isEqualTo(3);

        // Verify all agents ran successfully
        assertThat(result.agenticScope().readState("step1", false)).isTrue();
        assertThat(result.agenticScope().readState("step2", false)).isTrue();
        assertThat(result.agenticScope().readState("step3", false)).isTrue();
    }

    @Test
    @DisplayName("parallel_agent_with_4_agents_creates_exactly_4_tasks")
    void parallel_agent_with_4_agents_creates_exactly_4_tasks() {
        AtomicInteger executionCount = new AtomicInteger(0);

        var agent1 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("a", true);
        });

        var agent2 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("b", true);
        });

        var agent3 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("c", true);
        });

        var agent4 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("d", true);
        });

        var service = io.quarkiverse.flow.langchain4j.workflow.service.FlowParallelAgentService.builder(
                TestParallelAgent.class, runtimeAppProvider);

        service.subAgents(agent1, agent2, agent3, agent4);

        var agent = service.build();
        ResultWithAgenticScope<String> result = agent.run("test");

        // REGRESSION CHECK: All 4 parallel agents must execute
        assertThat(executionCount.get())
                .as("REGRESSION: Parallel workflow must have exactly 4 tasks for 4 agents")
                .isEqualTo(4);

        assertThat(result.agenticScope().readState("a", false)).isTrue();
        assertThat(result.agenticScope().readState("b", false)).isTrue();
        assertThat(result.agenticScope().readState("c", false)).isTrue();
        assertThat(result.agenticScope().readState("d", false)).isTrue();
    }

    @Test
    @DisplayName("loop_agent_with_2_agents_creates_exactly_2_tasks_per_iteration")
    void loop_agent_with_2_agents_creates_exactly_2_tasks_per_iteration() {
        AtomicInteger executionCount = new AtomicInteger(0);

        var agent1 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            int counter = scope.readState("counter", 0);
            scope.writeState("counter", counter + 1);
        });

        var agent2 = AgenticServices.agentAction(scope -> {
            executionCount.incrementAndGet();
            scope.writeState("step2_ran", true);
        });

        var service = io.quarkiverse.flow.langchain4j.workflow.service.FlowLoopAgentService.builder(
                TestLoopAgent.class, runtimeAppProvider);

        service.maxIterations(3);
        service.exitCondition((scope, idx) -> scope.readState("counter", 0) >= 3);
        service.subAgents(agent1, agent2);

        var agent = service.build();
        ResultWithAgenticScope<String> result = agent.run("test");

        // REGRESSION CHECK: Loop ran 3 iterations × 2 agents = 6 executions
        // If duplication bug returns, we'd see < 6 executions (phantom tasks would cause failures)
        assertThat(executionCount.get())
                .as("REGRESSION: Loop workflow must have exactly 2 tasks per iteration. " +
                        "Expected 3 iterations × 2 agents = 6 executions")
                .isEqualTo(6);

        assertThat(result.agenticScope().readState("counter", 0)).isEqualTo(3);
        assertThat(result.agenticScope().readState("step2_ran", false)).isTrue();
    }

    interface TestSequentialAgent {
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }

    interface TestParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }

    interface TestLoopAgent {
        ResultWithAgenticScope<String> run(@V("topic") String topic);
    }
}
