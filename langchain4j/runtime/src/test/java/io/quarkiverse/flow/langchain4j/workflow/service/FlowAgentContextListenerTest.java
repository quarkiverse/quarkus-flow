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
import io.quarkiverse.flow.langchain4j.FlowAgentListener;

class FlowAgentContextListenerTest {

    private static final String AGENT_ID = "agent-1";

    private final List<String> events = new ArrayList<>();
    private String currentContext = "root";
    private final AgenticScope agenticScope = mock(AgenticScope.class);
    private final AgenticScope otherAgenticScope = mock(AgenticScope.class);
    private final AgentInstance agent = agent(AGENT_ID);
    private final FlowAgentContextListener recordingContextListener = new FlowAgentContextListener(
            () -> new FlowAgentListenerDispatcher(List.of(new RecordingFlowAgentListener(events))));

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

    @Test
    @DisplayName("test_leaf_sub_agent_notifies_the_agent_invocation_listener_once_context_is_active")
    void test_leaf_sub_agent_notifies_the_agent_invocation_listener_once_context_is_active() {
        RecordingAgentInvocationListener.INVOKED_AGENT_NAMES.clear();
        AgentInstance leafSubAgent = mock(AgentInstance.class);
        when(leafSubAgent.agentId()).thenReturn("leaf-1");
        when(leafSubAgent.name()).thenReturn("leaf-1");
        when(leafSubAgent.leaf()).thenReturn(true);
        storeSnapshot("leaf-1", "leaf-task");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, leafSubAgent, Map.of()));

        assertThat(events).containsExactly("activate leaf-task");
        assertThat(RecordingAgentInvocationListener.INVOKED_AGENT_NAMES).containsExactly("leaf-1");
    }

    @Test
    @DisplayName("test_composite_sub_agent_does_not_notify_the_agent_invocation_listener")
    void test_composite_sub_agent_does_not_notify_the_agent_invocation_listener() {
        RecordingAgentInvocationListener.INVOKED_AGENT_NAMES.clear();
        storeSnapshot(AGENT_ID, "task-ctx");

        FlowAgentContextListener.INSTANCE.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));

        assertThat(RecordingAgentInvocationListener.INVOKED_AGENT_NAMES).isEmpty();
    }

    @Test
    @DisplayName("test_flow_agent_listener_is_notified_while_the_task_context_is_current")
    void test_flow_agent_listener_is_notified_while_the_task_context_is_current() {
        storeSnapshot(AGENT_ID, "task-ctx");

        recordingContextListener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        recordingContextListener.afterAgentInvocation(response(agent));

        assertThat(events).containsExactly(
                "activate task-ctx",
                "before agent-1",
                "activate task-ctx",
                "after agent-1",
                "close task-ctx",
                "close task-ctx");
    }

    @Test
    @DisplayName("test_flow_agent_listener_is_notified_of_a_failure_before_the_task_context_is_closed")
    void test_flow_agent_listener_is_notified_of_a_failure_before_the_task_context_is_closed() {
        storeSnapshot(AGENT_ID, "task-ctx");

        recordingContextListener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        recordingContextListener
                .onAgentInvocationError(new AgentInvocationError(agenticScope, agent, Map.of(), new RuntimeException()));

        assertThat(events).containsExactly(
                "activate task-ctx",
                "before agent-1",
                "activate task-ctx",
                "error agent-1",
                "close task-ctx",
                "close task-ctx");
    }

    @Test
    @DisplayName("test_flow_agent_listeners_of_nested_agents_see_their_own_task_context")
    void test_flow_agent_listeners_of_nested_agents_see_their_own_task_context() {
        AgentInstance inner = agent("agent-2");
        storeSnapshot(AGENT_ID, "outer-task");
        storeSnapshot("agent-2", "inner-task");

        recordingContextListener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        recordingContextListener.beforeAgentInvocation(new AgentRequest(agenticScope, inner, Map.of()));
        recordingContextListener.afterAgentInvocation(response(inner));
        recordingContextListener.afterAgentInvocation(response(agent));

        assertThat(events).containsExactly(
                "activate outer-task",
                "before agent-1",
                "activate inner-task",
                "before agent-2",
                "activate inner-task",
                "after agent-2",
                "close inner-task",
                "close inner-task",
                "activate outer-task",
                "after agent-1",
                "close outer-task",
                "close outer-task");
    }

    @Test
    @DisplayName("test_flow_agent_listener_is_notified_of_a_composite_agent_flow_planner_does_not_run")
    void test_flow_agent_listener_is_notified_of_a_composite_agent_flow_planner_does_not_run() {
        // the root agentic system: no stored context, nothing to activate
        recordingContextListener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        recordingContextListener.afterAgentInvocation(response(agent));
        recordingContextListener
                .onAgentInvocationError(new AgentInvocationError(agenticScope, agent, Map.of(), new RuntimeException()));

        assertThat(events).containsExactly("before agent-1", "after agent-1", "error agent-1");
    }

    @Test
    @DisplayName("test_flow_agent_listener_is_not_notified_by_the_context_listener_of_a_leaf_agent_flow_planner_does_not_run")
    void test_flow_agent_listener_is_not_notified_by_the_context_listener_of_a_leaf_agent_flow_planner_does_not_run() {
        // FlowAgentInvocationNotifier notifies those, on the thread that invoked them
        AgentInstance leaf = agent("leaf-1");
        when(leaf.leaf()).thenReturn(true);

        recordingContextListener.beforeAgentInvocation(new AgentRequest(agenticScope, leaf, Map.of()));
        recordingContextListener.afterAgentInvocation(response(leaf));
        recordingContextListener
                .onAgentInvocationError(new AgentInvocationError(agenticScope, leaf, Map.of(), new RuntimeException()));

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("test_failing_flow_agent_listener_neither_stops_the_others_nor_leaks_the_task_context")
    void test_failing_flow_agent_listener_neither_stops_the_others_nor_leaks_the_task_context() {
        FlowAgentListener failing = new FlowAgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                throw new IllegalStateException("before");
            }

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                throw new IllegalStateException("after");
            }
        };
        FlowAgentContextListener listener = new FlowAgentContextListener(
                () -> new FlowAgentListenerDispatcher(List.of(failing, new RecordingFlowAgentListener(events))));
        storeSnapshot(AGENT_ID, "task-ctx");

        listener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        listener.afterAgentInvocation(response(agent));

        assertThat(events).containsExactly(
                "activate task-ctx",
                "before agent-1",
                "activate task-ctx",
                "after agent-1",
                "close task-ctx",
                "close task-ctx");
    }

    @Test
    @DisplayName("test_flow_agent_listener_sees_the_task_context_after_another_listener_made_a_child_context_current")
    void test_flow_agent_listener_sees_the_task_context_after_another_listener_made_a_child_context_current() {
        List<String> seen = new ArrayList<>();
        FlowAgentContextListener listener = contextRecordingListener(seen);
        storeSnapshot(AGENT_ID, "task-ctx");

        listener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        // another AgentListener, notified between Flow's callbacks, makes a child span current until after Flow's
        // afterAgentInvocation
        try (FlowContextPropagator.Scope child = makeCurrent("child")) {
            listener.afterAgentInvocation(response(agent));
        }

        assertThat(seen).containsExactly("before agent-1 in task-ctx", "after agent-1 in task-ctx");
    }

    @Test
    @DisplayName("test_flow_agent_listener_sees_the_task_context_on_failure_after_another_listener_made_a_child_context_current")
    void test_flow_agent_listener_sees_the_task_context_on_failure_after_another_listener_made_a_child_context_current() {
        List<String> seen = new ArrayList<>();
        FlowAgentContextListener listener = contextRecordingListener(seen);
        storeSnapshot(AGENT_ID, "task-ctx");

        listener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, Map.of()));
        try (FlowContextPropagator.Scope child = makeCurrent("child")) {
            listener.onAgentInvocationError(new AgentInvocationError(agenticScope, agent, Map.of(), new RuntimeException()));
        }

        assertThat(seen).containsExactly("before agent-1 in task-ctx", "error agent-1 in task-ctx");
    }

    /**
     * A context listener whose only {@link FlowAgentListener} records, as {@code "<callback> <agent id> in <context>"},
     * the fake context current in each callback.
     */
    private FlowAgentContextListener contextRecordingListener(List<String> seen) {
        FlowAgentListener recording = new FlowAgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                seen.add("before " + request.agentId() + " in " + currentContext);
            }

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                seen.add("after " + response.agentId() + " in " + currentContext);
            }

            @Override
            public void onAgentInvocationError(AgentInvocationError error) {
                seen.add("error " + error.agentId() + " in " + currentContext);
            }
        };
        return new FlowAgentContextListener(() -> new FlowAgentListenerDispatcher(List.of(recording)));
    }

    private void storeSnapshot(String agentId, String name) {
        storeSnapshot(agenticScope, agentId, name);
    }

    private void storeSnapshot(AgenticScope scope, String agentId, String name) {
        FlowContextPropagator.Snapshot snapshot = () -> {
            events.add("activate " + name);
            FlowContextPropagator.Scope restore = makeCurrent(name);
            return () -> {
                restore.close();
                events.add("close " + name);
            };
        };
        when(scope.executionContext(FlowAgentContextListener.contextKey(agentId))).thenReturn(snapshot);
    }

    /**
     * Makes {@code name} the fake current context until the returned scope is closed, as {@code Context.makeCurrent()}
     * does, for example when another listener makes a child span current.
     */
    private FlowContextPropagator.Scope makeCurrent(String name) {
        String previous = currentContext;
        currentContext = name;
        return () -> currentContext = previous;
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
