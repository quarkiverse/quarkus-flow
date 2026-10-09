package io.quarkiverse.flow.langchain4j.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.internal.FlowContextPropagator;

class FlowAgentContextListenerTest {

    private static final String AGENT_ID = "agent-1";

    private final List<String> events = new ArrayList<>();
    private final AgenticScope agenticScope = mock(AgenticScope.class);
    private final AgenticScope otherAgenticScope = mock(AgenticScope.class);
    private final AgentInstance agent = agent(AGENT_ID);

    @AfterEach
    void drainOpenScopes() {
        // never leak a scope into another test running on this thread
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(agenticScope);
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(otherAgenticScope);
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(agenticScope);
    }

    @Test
    @DisplayName("test_context_of_requesting_task_is_current_during_agent_invocation")
    void test_context_of_requesting_task_is_current_during_agent_invocation() {
        storeSnapshot(AGENT_ID, "task-ctx");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        assertThat(events).containsExactly("activate task-ctx");

        FlowAgentContextListener.INSTANCE.afterAgentInvocation(response(agent));
        assertThat(events).containsExactly("activate task-ctx", "close task-ctx");
    }

    @Test
    @DisplayName("test_context_is_closed_when_agent_invocation_fails")
    void test_context_is_closed_when_agent_invocation_fails() {
        storeSnapshot(AGENT_ID, "task-ctx");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        FlowAgentContextListener.INSTANCE
                .onAgentInvocationError(new AgentInvocationError(agenticScope, agent, Map.of(), new RuntimeException()));

        assertThat(events).containsExactly("activate task-ctx", "close task-ctx");
    }

    @Test
    @DisplayName("test_context_is_closed_when_agentic_system_suspends")
    void test_context_is_closed_when_agentic_system_suspends() {
        storeSnapshot(AGENT_ID, "task-ctx");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(agenticScope);

        assertThat(events).containsExactly("activate task-ctx", "close task-ctx");
    }

    @Test
    @DisplayName("test_nested_suspension_closes_each_abandoned_context_once")
    void test_nested_suspension_closes_each_abandoned_context_once() {
        // sequence(..., extract = parallel(...)) on one thread, all levels sharing one AgenticScope
        AgentInstance extract = agent("extract-1");
        storeSnapshot("extract-1", "extract-task");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, extract, Map.of()));
        // the nested system suspends, then its parent, then the root call: one callback per level
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(agenticScope);
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(agenticScope);
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(agenticScope);

        assertThat(events).containsExactly("activate extract-task", "close extract-task");
    }

    @Test
    @DisplayName("test_suspension_never_closes_the_context_of_an_enclosing_agentic_system")
    void test_suspension_never_closes_the_context_of_an_enclosing_agentic_system() {
        // an agent of one agentic system invokes a second, unrelated agentic system (its own AgenticScope) that
        // suspends; the root call of the second system has no context of its own
        storeSnapshot(AGENT_ID, "outer-task");
        AgentInstance inner = agent("inner-agent");
        storeSnapshot(otherAgenticScope, "inner-agent", "inner-task");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(otherAgenticScope, inner, Map.of()));
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(otherAgenticScope);
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(otherAgenticScope);

        assertThat(events).containsExactly("activate outer-task", "activate inner-task", "close inner-task");

        FlowAgentContextListener.INSTANCE.afterAgentInvocation(response(agent));
        assertThat(events).endsWith("close outer-task");
    }

    @Test
    @DisplayName("test_suspension_of_a_system_without_open_contexts_closes_nothing")
    void test_suspension_of_a_system_without_open_contexts_closes_nothing() {
        storeSnapshot(AGENT_ID, "task-ctx");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        FlowAgentContextListener.INSTANCE.onAgenticSystemSuspended(otherAgenticScope);

        assertThat(events).containsExactly("activate task-ctx");
    }

    @Test
    @DisplayName("test_agents_not_dispatched_by_flow_planner_are_left_alone")
    void test_agents_not_dispatched_by_flow_planner_are_left_alone() {
        AgentInstance outer = agent("outer");
        storeSnapshot(AGENT_ID, "task-ctx");

        // the root agentic system invocation has no stored context: nothing is opened, nothing is closed
        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, outer, Map.of()));
        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        FlowAgentContextListener.INSTANCE.afterAgentInvocation(response(agent));
        FlowAgentContextListener.INSTANCE.afterAgentInvocation(response(outer));

        assertThat(events).containsExactly("activate task-ctx", "close task-ctx");
    }

    @Test
    @DisplayName("test_nested_agents_close_in_reverse_order")
    void test_nested_agents_close_in_reverse_order() {
        AgentInstance inner = agent("agent-2");
        storeSnapshot(AGENT_ID, "outer-task");
        storeSnapshot("agent-2", "inner-task");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, inner, Map.of()));
        FlowAgentContextListener.INSTANCE.afterAgentInvocation(response(inner));
        FlowAgentContextListener.INSTANCE.afterAgentInvocation(response(agent));

        assertThat(events).containsExactly("activate outer-task", "activate inner-task", "close inner-task",
                "close outer-task");
    }

    @Test
    @DisplayName("test_listener_is_inherited_by_subagents")
    void test_listener_is_inherited_by_subagents() {
        assertThat(FlowAgentContextListener.INSTANCE.inheritedBySubagents()).isTrue();
    }

    private void storeSnapshot(String agentId, String name) {
        storeSnapshot(agenticScope, agentId, name);
    }

    private void storeSnapshot(AgenticScope scope, String agentId, String name) {
        FlowContextPropagator.Snapshot snapshot = () -> {
            events.add("activate " + name);
            return () -> events.add("close " + name);
        };
        when(scope.executionContext(FlowAgentContextListener.contextKey(agentId))).thenReturn(snapshot);
    }

    private AgentResponse response(AgentInstance agentInstance) {
        return new AgentResponse(agenticScope, agentInstance, Map.of(), null, null, null);
    }

    private static AgentInstance agent(String agentId) {
        AgentInstance instance = mock(AgentInstance.class);
        when(instance.agentId()).thenReturn(agentId);
        when(instance.name()).thenReturn(agentId);
        return instance;
    }
}
