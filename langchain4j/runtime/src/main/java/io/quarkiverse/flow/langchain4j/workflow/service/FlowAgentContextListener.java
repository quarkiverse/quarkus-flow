package io.quarkiverse.flow.langchain4j.workflow.service;

import java.util.ArrayDeque;
import java.util.Deque;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.internal.FlowContextPropagator;

/**
 * Makes the context of the workflow task that requested an agent current while that agent runs.
 * <p>
 * {@link FlowPlanner} hands agent invocations from workflow tasks to LangChain4j, which runs them on the planner thread
 * or on its own executor. The planner stores the requesting task's {@link FlowContextPropagator.Snapshot} in the
 * {@link AgenticScope} execution context under {@link #contextKey(String)}; this listener activates it in
 * {@link #beforeAgentInvocation(AgentRequest)} and closes it when the invocation ends, on the same thread.
 */
public final class FlowAgentContextListener implements AgentListener {

    public static final FlowAgentContextListener INSTANCE = new FlowAgentContextListener();

    static final String CONTEXT_KEY_PREFIX = "flow.agent.context:";

    private static final ThreadLocal<Deque<OpenScope>> OPEN_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    private FlowAgentContextListener() {
    }

    static String contextKey(String agentId) {
        return CONTEXT_KEY_PREFIX + agentId;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        FlowContextPropagator.Snapshot snapshot = lookup(request.agenticScope(), request.agentId());
        if (snapshot != null) {
            OPEN_SCOPES.get().push(new OpenScope(request.agentId(), snapshot.activate()));
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        close(response.agentId());
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        close(error.agentId());
    }

    @Override
    public void onAgenticSystemSuspended(AgenticScope agenticScope) {
        // A suspended invocation ends without afterAgentInvocation/onAgentInvocationError and carries no agent id
        Deque<OpenScope> scopes = OPEN_SCOPES.get();
        if (!scopes.isEmpty()) {
            scopes.pop().scope().close();
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    private static FlowContextPropagator.Snapshot lookup(AgenticScope agenticScope, String agentId) {
        if (agenticScope == null || agentId == null) {
            return null;
        }
        return agenticScope.executionContext(contextKey(agentId)) instanceof FlowContextPropagator.Snapshot snapshot
                ? snapshot
                : null;
    }

    private static void close(String agentId) {
        Deque<OpenScope> scopes = OPEN_SCOPES.get();
        if (!scopes.isEmpty() && scopes.peek().agentId().equals(agentId)) {
            scopes.pop().scope().close();
        }
    }

    private record OpenScope(String agentId, FlowContextPropagator.Scope scope) {
    }
}
