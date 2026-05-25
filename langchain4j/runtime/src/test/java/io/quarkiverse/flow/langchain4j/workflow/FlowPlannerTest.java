package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import io.serverlessworkflow.impl.WorkflowDefinition;

/**
 * Unit tests for {@link FlowPlanner}.
 */
@DisplayName("FlowPlanner")
class FlowPlannerTest {

    private AgenticFlow mockFlow;

    @BeforeEach
    void setUp() {
        mockFlow = mock(SequentialAgenticFlow.class);
        WorkflowDefinition mockWorkflow = mock(WorkflowDefinition.class);
        when(mockFlow.definition()).thenReturn(mockWorkflow);
    }

    @Test
    @DisplayName("getAgentByIndex should return agent instance for valid index")
    void test_getAgentByIndex_validIndex() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);

        AgentInstance mockAgent0 = createMockAgent("generateStory$0");
        AgentInstance mockAgent1 = createMockAgent("editStory$1");
        InitPlanningContext initContext = createInitContext(List.of(mockAgent0, mockAgent1));
        planner.init(initContext);

        AgentInstance agent = planner.getAgentByIndex(0);

        assertThat(agent).isNotNull();
        assertThat(agent.agentId()).isEqualTo("generateStory$0");
    }

    @Test
    @DisplayName("getAgentByIndex should throw IllegalStateException for negative index")
    void test_getAgentByIndex_negativeIndex() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);

        AgentInstance agent = createMockAgent("agent$0");
        planner.init(createInitContext(List.of(agent)));

        assertThatThrownBy(() -> planner.getAgentByIndex(-1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid subagent index: -1")
                .hasMessageContaining("Available subagents: 1");
    }

    @Test
    @DisplayName("getAgentByIndex should throw IllegalStateException for out of bounds index")
    void test_getAgentByIndex_outOfBounds() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);

        List<AgentInstance> agents = List.of(
                createMockAgent("agent1$0"),
                createMockAgent("agent2$1"),
                createMockAgent("agent3$2"));
        planner.init(createInitContext(agents));

        assertThatThrownBy(() -> planner.getAgentByIndex(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid subagent index: 5")
                .hasMessageContaining("Available subagents: 3")
                .hasMessageContaining("agent1$0")
                .hasMessageContaining("agent2$1")
                .hasMessageContaining("agent3$2");
    }

    @Test
    @DisplayName("executeAgent should fail when planner is closed")
    void test_executeAgent_plannerClosed() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);
        AgentInstance mockAgent = createMockAgent("test$0");
        planner.init(createInitContext(List.of(mockAgent)));
        planner.close();

        CompletableFuture<Void> future = planner.executeAgent(mockAgent);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .isInstanceOf(CancellationException.class)
                .withStackTraceContaining("Planner is closed");
    }

    @Test
    @DisplayName("topology should return the correct topology")
    void test_topology() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.PARALLEL, mockFlow);

        assertThat(planner.topology()).isEqualTo(AgenticSystemTopology.PARALLEL);
    }

    @Test
    @DisplayName("terminated should return false before close")
    void test_terminated_beforeClose() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);
        planner.init(createInitContext(List.of()));

        assertThat(planner.terminated()).isFalse();
    }

    @Test
    @DisplayName("terminated should return true after close")
    void test_terminated_afterClose() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);
        planner.init(createInitContext(List.of()));
        planner.close();

        assertThat(planner.terminated()).isTrue();
    }

    @Test
    @DisplayName("close should be idempotent")
    void test_close_idempotent() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);
        planner.init(createInitContext(List.of()));

        // Should not throw when called multiple times
        planner.close();
        planner.close();
        planner.close();

        assertThat(planner.terminated()).isTrue();
    }

    @Test
    @DisplayName("executeAgent should fail all pending agents when aborted")
    void test_abort_failsPendingAgents() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);
        AgentInstance mockAgent = createMockAgent("test$0");
        planner.init(createInitContext(List.of(mockAgent)));

        // Queue up an agent execution
        CompletableFuture<Void> future = planner.executeAgent(mockAgent);

        // Abort with an exception
        RuntimeException abortCause = new RuntimeException("Test abort");
        planner.abort(abortCause);

        // The pending future should complete exceptionally
        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .withCauseInstanceOf(RuntimeException.class)
                .withMessageContaining("Test abort");
    }

    @Test
    @DisplayName("init should build agent list from context")
    void test_init_buildsAgentList() {
        FlowPlanner planner = new FlowPlanner(AgenticSystemTopology.SEQUENCE, mockFlow);

        List<AgentInstance> agents = List.of(
                createMockAgent("agent1$0"),
                createMockAgent("agent2$1"));
        planner.init(createInitContext(agents));

        // Verify we can retrieve both agents by index
        assertThat(planner.getAgentByIndex(0)).isNotNull();
        assertThat(planner.getAgentByIndex(0).agentId()).isEqualTo("agent1$0");
        assertThat(planner.getAgentByIndex(1)).isNotNull();
        assertThat(planner.getAgentByIndex(1).agentId()).isEqualTo("agent2$1");
    }

    // ========== Helper Methods ==========

    private AgentInstance createMockAgent(String agentId) {
        AgentInstance mock = mock(AgentInstance.class);
        when(mock.agentId()).thenReturn(agentId);
        return mock;
    }

    private InitPlanningContext createInitContext(List<AgentInstance> agents) {
        InitPlanningContext mock = mock(InitPlanningContext.class);
        when(mock.subagents()).thenReturn(agents);
        return mock;
    }
}
