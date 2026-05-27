package io.quarkiverse.flow.langchain4j.workflow.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.service.V;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for {@link FlowAgentsBuilderService}.
 * <p>
 * This service bridges LangChain4j's WorkflowAgentsBuilder with CDI, providing
 * access to both build-time generated flows and runtime-created UntypedAgent flows.
 */
@QuarkusTest
@DisplayName("FlowAgentsBuilderService tests")
public class FlowAgentsBuilderServiceTest {

    @Inject
    FlowAgentsBuilderService builderService;

    // ==================== Sequential Agent Tests ====================

    @Test
    @DisplayName("newSequential_for_UntypedAgent_returns_builder")
    void newSequential_for_UntypedAgent_returns_builder() {
        SequentialAgentService<UntypedAgent> service = builderService.newSequential();

        assertThat(service)
                .as("newSequential() should return a builder for UntypedAgent")
                .isNotNull();
    }

    @Test
    @DisplayName("newSequential_UntypedAgent_can_build_and_execute")
    void newSequential_UntypedAgent_can_build_and_execute() {
        var agent1 = AgenticServices.agentAction(scope -> scope.writeState("step", "1"));
        var agent2 = AgenticServices.agentAction(scope -> scope.writeState("step", "2"));

        UntypedAgent agent = builderService.newSequential()
                .subAgents(agent1, agent2)
                .build();

        ResultWithAgenticScope<String> result = agent.invokeWithAgenticScope(java.util.Map.of());

        assertThat(result.agenticScope().readState("step", ""))
                .as("UntypedAgent sequential workflow should execute all agents")
                .isEqualTo("2");
    }

    @Test
    @DisplayName("newSequential_with_nonexistent_class_throws_IllegalStateException")
    void newSequential_with_nonexistent_class_throws_IllegalStateException() {
        assertThatThrownBy(() -> builderService.newSequential(NonExistentSequentialAgent.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No sequential flow found for agent")
                .hasMessageContaining(NonExistentSequentialAgent.class.getName())
                .hasMessageContaining("@SequentialAgent annotation");
    }

    // ==================== Parallel Agent Tests ====================

    @Test
    @DisplayName("newParallel_for_UntypedAgent_returns_builder")
    void newParallel_for_UntypedAgent_returns_builder() {
        ParallelAgentService<UntypedAgent> service = builderService.newParallel();

        assertThat(service)
                .as("newParallel() should return a builder for UntypedAgent")
                .isNotNull();
    }

    @Test
    @DisplayName("newParallel_UntypedAgent_can_build_and_execute")
    void newParallel_UntypedAgent_can_build_and_execute() {
        var agent1 = AgenticServices.agentAction(scope -> scope.writeState("a", true));
        var agent2 = AgenticServices.agentAction(scope -> scope.writeState("b", true));

        UntypedAgent agent = builderService.newParallel()
                .subAgents(agent1, agent2)
                .build();

        ResultWithAgenticScope<String> result = agent.invokeWithAgenticScope(java.util.Map.of());

        assertThat(result.agenticScope().readState("a", false)).isTrue();
        assertThat(result.agenticScope().readState("b", false)).isTrue();
    }

    @Test
    @DisplayName("newParallel_with_nonexistent_class_throws_IllegalStateException")
    void newParallel_with_nonexistent_class_throws_IllegalStateException() {
        assertThatThrownBy(() -> builderService.newParallel(NonExistentParallelAgent.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No parallel flow found for agent")
                .hasMessageContaining(NonExistentParallelAgent.class.getName())
                .hasMessageContaining("@ParallelAgent annotation");
    }

    // ==================== Loop Agent Tests ====================

    @Test
    @DisplayName("newLoop_for_UntypedAgent_returns_builder")
    void newLoop_for_UntypedAgent_returns_builder() {
        LoopAgentService<UntypedAgent> service = builderService.newLoop();

        assertThat(service)
                .as("newLoop() should return a builder for UntypedAgent")
                .isNotNull();
    }

    @Test
    @DisplayName("newLoop_UntypedAgent_can_build_and_execute")
    void newLoop_UntypedAgent_can_build_and_execute() {
        var loopAgent = AgenticServices.agentAction(scope -> {
            int counter = scope.readState("counter", 0);
            scope.writeState("counter", counter + 1);
        });

        UntypedAgent agent = builderService.newLoop()
                .maxIterations(3)
                .exitCondition((scope, iteration) -> scope.readState("counter", 0) >= 3)
                .subAgents(loopAgent)
                .build();

        ResultWithAgenticScope<String> result = agent.invokeWithAgenticScope(java.util.Map.of());

        assertThat(result.agenticScope().readState("counter", 0))
                .as("UntypedAgent loop workflow should iterate 3 times")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("newLoop_with_nonexistent_class_throws_IllegalStateException")
    void newLoop_with_nonexistent_class_throws_IllegalStateException() {
        assertThatThrownBy(() -> builderService.newLoop(NonExistentLoopAgent.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No loop flow found for agent")
                .hasMessageContaining(NonExistentLoopAgent.class.getName())
                .hasMessageContaining("@LoopAgent annotation");
    }

    // ==================== Conditional Agent Tests ====================

    @Test
    @DisplayName("newConditional_for_UntypedAgent_returns_builder")
    void newConditional_for_UntypedAgent_returns_builder() {
        ConditionalAgentService<UntypedAgent> service = builderService.newConditional();

        assertThat(service)
                .as("newConditional() should return a builder for UntypedAgent")
                .isNotNull();
    }

    @Test
    @DisplayName("newConditional_UntypedAgent_can_build_and_execute")
    void newConditional_UntypedAgent_can_build_and_execute() {
        var branchA = AgenticServices.agentAction(scope -> scope.writeState("branch", "A"));
        var branchB = AgenticServices.agentAction(scope -> scope.writeState("branch", "B"));

        UntypedAgent agent = builderService.newConditional()
                .subAgents(scope -> "A".equals(scope.readState("type", "")), branchA)
                .subAgents(scope -> "B".equals(scope.readState("type", "")), branchB)
                .build();

        // Test branch A
        ResultWithAgenticScope<String> resultA = agent.invokeWithAgenticScope(java.util.Map.of("type", "A"));
        assertThat(resultA.agenticScope().readState("branch", ""))
                .as("Conditional should route to branch A")
                .isEqualTo("A");

        // Test branch B
        ResultWithAgenticScope<String> resultB = agent.invokeWithAgenticScope(java.util.Map.of("type", "B"));
        assertThat(resultB.agenticScope().readState("branch", ""))
                .as("Conditional should route to branch B")
                .isEqualTo("B");
    }

    @Test
    @DisplayName("newConditional_with_nonexistent_class_throws_IllegalStateException")
    void newConditional_with_nonexistent_class_throws_IllegalStateException() {
        assertThatThrownBy(() -> builderService.newConditional(NonExistentConditionalAgent.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No conditional flow found for agent")
                .hasMessageContaining(NonExistentConditionalAgent.class.getName())
                .hasMessageContaining("@ConditionalAgent annotation");
    }

    // ==================== Error Message Quality Tests ====================

    @Test
    @DisplayName("error_message_includes_available_flows_when_present")
    void error_message_includes_available_flows_when_present() {
        // This test verifies the error message quality when flows DO exist but not for the requested class
        // The error should list what IS available to help debugging
        assertThatThrownBy(() -> builderService.newSequential(NonExistentSequentialAgent.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Available sequential flows:");
    }

    @Test
    @DisplayName("capitalize_helper_capitalizes_topology_names")
    void capitalize_helper_capitalizes_topology_names() {
        // Test the capitalize helper through the error messages
        assertThatThrownBy(() -> builderService.newSequential(NonExistentSequentialAgent.class))
                .hasMessageContaining("@SequentialAgent");

        assertThatThrownBy(() -> builderService.newParallel(NonExistentParallelAgent.class))
                .hasMessageContaining("@ParallelAgent");

        assertThatThrownBy(() -> builderService.newLoop(NonExistentLoopAgent.class))
                .hasMessageContaining("@LoopAgent");

        assertThatThrownBy(() -> builderService.newConditional(NonExistentConditionalAgent.class))
                .hasMessageContaining("@ConditionalAgent");
    }

    // ==================== Integration Test ====================

    @Test
    @DisplayName("all_four_topology_builders_work_independently")
    void all_four_topology_builders_work_independently() {
        // Verify we can create all four topology types and they don't interfere with each other
        var testAgent = AgenticServices.agentAction(scope -> scope.writeState("executed", true));

        UntypedAgent sequential = builderService.newSequential()
                .subAgents(testAgent)
                .build();

        UntypedAgent parallel = builderService.newParallel()
                .subAgents(testAgent)
                .build();

        UntypedAgent loop = builderService.newLoop()
                .maxIterations(1)
                // exitCondition is optional - defaults to running until maxIterations
                .subAgents(testAgent)
                .build();

        UntypedAgent conditional = builderService.newConditional()
                .subAgents(scope -> true, testAgent)
                .build();

        // All should execute successfully
        assertThat(sequential.invokeWithAgenticScope(java.util.Map.of()).agenticScope().readState("executed", false)).isTrue();
        assertThat(parallel.invokeWithAgenticScope(java.util.Map.of()).agenticScope().readState("executed", false)).isTrue();
        assertThat(loop.invokeWithAgenticScope(java.util.Map.of()).agenticScope().readState("executed", false)).isTrue();
        assertThat(conditional.invokeWithAgenticScope(java.util.Map.of()).agenticScope().readState("executed", false)).isTrue();
    }

    // ==================== Test Helper Interfaces ====================

    // These interfaces are used only for testing error scenarios
    interface NonExistentSequentialAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }

    interface NonExistentParallelAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }

    interface NonExistentLoopAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }

    interface NonExistentConditionalAgent {
        ResultWithAgenticScope<String> run(@V("input") String input);
    }
}
