package io.quarkiverse.flow.langchain4j.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.internal.FlowContextPropagator;

/**
 * Verifies that {@link FlowAgentInvocationNotifier} notifies {@link io.quarkiverse.flow.internal.FlowAgentInvocationListener}
 * for leaf agents invoked directly from a task body, and stays silent for composite agents and for leaf agents dispatched
 * by {@code FlowPlanner}, which {@link FlowAgentContextListener} notifies instead once it restores the task context.
 * It also verifies that it notifies the {@link io.quarkiverse.flow.langchain4j.FlowAgentListener} beans of the same leaf
 * agents, and only of those.
 */
class FlowAgentInvocationNotifierTest {

    private static final String AGENT_ID = "agent-1";

    private final List<String> events = new ArrayList<>();
    private final FlowAgentInvocationNotifier notifier = new FlowAgentInvocationNotifier(
            new FlowAgentListenerDispatcher(List.of(new RecordingFlowAgentListener(events))));
    private final AgenticScope agenticScope = mock(AgenticScope.class);

    @BeforeEach
    void clearRecordedInvocations() {
        RecordingAgentInvocationListener.INVOKED_AGENT_NAMES.clear();
    }

    @Test
    @DisplayName("test_composite_agent_is_not_notified")
    void test_composite_agent_is_not_notified() {
        AgentInstance composite = agent(AGENT_ID, false);

        notifier.beforeAgentInvocation(new AgentRequest(agenticScope, composite, Map.of()));

        assertThat(RecordingAgentInvocationListener.INVOKED_AGENT_NAMES).isEmpty();
    }

    @Test
    @DisplayName("test_leaf_agent_invoked_directly_is_notified")
    void test_leaf_agent_invoked_directly_is_notified() {
        AgentInstance leaf = agent(AGENT_ID, true);

        notifier.beforeAgentInvocation(new AgentRequest(agenticScope, leaf, Map.of()));

        assertThat(RecordingAgentInvocationListener.INVOKED_AGENT_NAMES).containsExactly(AGENT_ID);
    }

    @Test
    @DisplayName("test_leaf_agent_dispatched_by_flow_planner_is_left_to_the_context_listener")
    void test_leaf_agent_dispatched_by_flow_planner_is_left_to_the_context_listener() {
        AgentInstance leaf = agent(AGENT_ID, true);
        FlowContextPropagator.Snapshot snapshot = () -> () -> {
        };
        when(agenticScope.executionContext(FlowAgentContextListener.contextKey(AGENT_ID))).thenReturn(snapshot);

        notifier.beforeAgentInvocation(new AgentRequest(agenticScope, leaf, Map.of()));

        assertThat(RecordingAgentInvocationListener.INVOKED_AGENT_NAMES).isEmpty();
    }

    @Test
    @DisplayName("test_flow_agent_listener_is_notified_of_a_leaf_agent_invoked_directly")
    void test_flow_agent_listener_is_notified_of_a_leaf_agent_invoked_directly() {
        AgentInstance leaf = agent(AGENT_ID, true);

        notifier.beforeAgentInvocation(new AgentRequest(agenticScope, leaf, Map.of()));
        notifier.afterAgentInvocation(new AgentResponse(agenticScope, leaf, Map.of(), null, null, null));
        notifier.onAgentInvocationError(new AgentInvocationError(agenticScope, leaf, Map.of(), new RuntimeException()));

        assertThat(events).containsExactly("before agent-1", "after agent-1", "error agent-1");
    }

    @Test
    @DisplayName("test_flow_agent_listener_is_left_to_the_context_listener_for_composite_agents")
    void test_flow_agent_listener_is_left_to_the_context_listener_for_composite_agents() {
        AgentInstance composite = agent(AGENT_ID, false);

        notifier.beforeAgentInvocation(new AgentRequest(agenticScope, composite, Map.of()));
        notifier.afterAgentInvocation(new AgentResponse(agenticScope, composite, Map.of(), null, null, null));
        notifier.onAgentInvocationError(
                new AgentInvocationError(agenticScope, composite, Map.of(), new RuntimeException()));

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("test_flow_agent_listener_is_left_to_the_context_listener_for_agents_flow_planner_runs")
    void test_flow_agent_listener_is_left_to_the_context_listener_for_agents_flow_planner_runs() {
        AgentInstance leaf = agent(AGENT_ID, true);
        FlowContextPropagator.Snapshot snapshot = () -> () -> {
        };
        when(agenticScope.executionContext(FlowAgentContextListener.contextKey(AGENT_ID))).thenReturn(snapshot);

        notifier.beforeAgentInvocation(new AgentRequest(agenticScope, leaf, Map.of()));
        notifier.afterAgentInvocation(new AgentResponse(agenticScope, leaf, Map.of(), null, null, null));
        notifier.onAgentInvocationError(new AgentInvocationError(agenticScope, leaf, Map.of(), new RuntimeException()));

        assertThat(events).isEmpty();
    }

    private static AgentInstance agent(String agentId, boolean leaf) {
        AgentInstance instance = mock(AgentInstance.class);
        when(instance.agentId()).thenReturn(agentId);
        when(instance.name()).thenReturn(agentId);
        when(instance.leaf()).thenReturn(leaf);
        return instance;
    }
}
