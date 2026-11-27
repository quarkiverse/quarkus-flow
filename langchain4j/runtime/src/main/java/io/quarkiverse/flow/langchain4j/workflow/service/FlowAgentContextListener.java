package io.quarkiverse.flow.langchain4j.workflow.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkiverse.flow.internal.FlowAgentInvocationListener;
import io.quarkiverse.flow.internal.FlowContextPropagator;
import io.quarkiverse.flow.langchain4j.FlowAgentListener;

/**
 * Makes the context of the workflow task that requested an agent current while that agent runs.
 * <p>
 * {@link FlowPlanner} hands agent invocations from workflow tasks to LangChain4j, which runs them on the planner thread
 * or on its own executor. The planner stores the requesting task's {@link FlowContextPropagator.Snapshot} in the
 * {@link AgenticScope} execution context under {@link #contextKey(String)}; this listener activates it in
 * {@link #beforeAgentInvocation(AgentRequest)} and closes it when the invocation ends, on the same thread.
 * <p>
 * It also tells a {@link FlowPlanner} when the agentic system it runs has failed (see
 * {@link #onAgentInvocationError(AgentInvocationError)}).
 * <p>
 * Once the requesting task's context is current, it notifies {@link FlowAgentInvocationListener} for leaf agents, so
 * leaf sub-agents of a generated agentic workflow are detected the same way as leaf agents invoked directly from a task
 * body (see {@link FlowAgentInvocationNotifier}, which covers that case).
 * <p>
 * It notifies the {@link FlowAgentListener} beans of the agents it opened a context for while that context is current:
 * after activating it, and before closing it. Another {@code AgentListener} may have made a child context current in
 * between, such as a span of its own, so it makes the snapshot current again around the completion and failure
 * callbacks. It also notifies them of the composite agents {@code FlowPlanner} doesn't
 * run, such as the root agentic system, on the thread that invoked them: it is registered on every agentic system that
 * Quarkus Flow builds, while LangChain4j doesn't give the {@code AgentListener} beans, and so
 * {@link FlowAgentInvocationNotifier}, to composite agents before Quarkus LangChain4j 2.0.
 */
public final class FlowAgentContextListener implements AgentListener {

    public static final FlowAgentContextListener INSTANCE = new FlowAgentContextListener(
            FlowAgentListenerDispatcher::fromContainer);

    static final String CONTEXT_KEY_PREFIX = "flow.agent.context:";

    private static final ThreadLocal<Deque<OpenScope>> OPEN_SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    private final Supplier<FlowAgentListenerDispatcher> dispatcher;

    FlowAgentContextListener(Supplier<FlowAgentListenerDispatcher> dispatcher) {
        this.dispatcher = dispatcher;
    }

    static String contextKey(String agentId) {
        return CONTEXT_KEY_PREFIX + agentId;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        FlowContextPropagator.Snapshot snapshot = lookup(request.agenticScope(), request.agentId());
        if (snapshot != null) {
            OPEN_SCOPES.get()
                    .push(new OpenScope(request.agenticScope(), request.agentId(), snapshot, snapshot.activate()));
            if (request.agent().leaf()) {
                FlowAgentInvocationListener.current().onAgentInvoked(request.agentName());
            }
            dispatcher.get().beforeAgentInvocation(request);
        } else if (!request.agent().leaf()) {
            dispatcher.get().beforeAgentInvocation(request);
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        try {
            FlowAgentListenerDispatcher listeners = dispatcher.get();
            if (isOpenFor(response.agentId()) && listeners.hasListeners()) {
                try (FlowContextPropagator.Scope ignored = reactivate()) {
                    listeners.afterAgentInvocation(response);
                }
            } else if (!response.agent().leaf()) {
                listeners.afterAgentInvocation(response);
            }
        } finally {
            close(response.agentId());
        }
    }

    /**
     * Closes the failed agent's context and, if the agent is an agentic system run by a {@link FlowPlanner}, fails
     * the agent calls its workflow is still waiting for, so the workflow ends.
     * <p>
     * An agentic system's failure is reported only once its planning loop has ended, whatever its parent's error handler
     * then decides; a leaf agent's failure is reported before the error handler runs, but a leaf has no planner.
     */
    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        try {
            FlowAgentListenerDispatcher listeners = dispatcher.get();
            if (isOpenFor(error.agentId()) && listeners.hasListeners()) {
                try (FlowContextPropagator.Scope ignored = reactivate()) {
                    listeners.onAgentInvocationError(error);
                }
            } else if (!error.agent().leaf()) {
                listeners.onAgentInvocationError(error);
            }
        } finally {
            close(error.agentId());
            FlowPlanner.abort(error.agenticScope(), error.agentId(), error.error());
        }
    }

    /**
     * Closes the contexts this thread opened for agents of the suspended agentic system.
     * <p>
     * A suspending agentic system throws {@code AgenticSystemSuspendedException} instead of returning, so the agents
     * it was running never get {@code afterAgentInvocation}/{@code onAgentInvocationError}. Every level of a nested
     * agentic system shares the same {@link AgenticScope}, and each level fires this callback as the suspension
     * unwinds up to the root call, which never opened a context. Closing only the entries opened for
     * {@code agenticScope}, from the top of this thread's stack down to the first entry of another scope, closes
     * each abandoned context exactly once, in reverse opening order, and never touches the context of an enclosing,
     * unrelated agentic system.
     */
    @Override
    public void onAgenticSystemSuspended(AgenticScope agenticScope) {
        Deque<OpenScope> scopes = OPEN_SCOPES.get();
        while (!scopes.isEmpty() && scopes.peek().agenticScope() == agenticScope) {
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

    private static boolean isOpenFor(String agentId) {
        Deque<OpenScope> scopes = OPEN_SCOPES.get();
        return !scopes.isEmpty() && scopes.peek().agentId().equals(agentId);
    }

    /**
     * Makes the snapshot of the innermost open context current again, on top of whatever another listener has made
     * current since it was activated.
     */
    private static FlowContextPropagator.Scope reactivate() {
        return OPEN_SCOPES.get().peek().snapshot().activate();
    }

    private static void close(String agentId) {
        if (isOpenFor(agentId)) {
            OPEN_SCOPES.get().pop().scope().close();
        }
    }

    private record OpenScope(AgenticScope agenticScope, String agentId, FlowContextPropagator.Snapshot snapshot,
            FlowContextPropagator.Scope scope) {
    }
}
