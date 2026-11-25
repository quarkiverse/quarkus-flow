package io.quarkiverse.flow.langchain4j.workflow.service;

import java.util.List;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import io.quarkiverse.flow.langchain4j.FlowAgentListener;
import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * Notifies the {@link FlowAgentListener} beans of an agent invocation, isolating each one from the failures of the
 * others.
 * <p>
 * {@link FlowAgentContextListener} calls it for the agents {@link FlowPlanner} runs, while the requesting task's context
 * is current, and for the other composite agents Quarkus Flow builds. {@link FlowAgentInvocationNotifier} calls it for
 * the other leaf agents.
 */
@Singleton
public class FlowAgentListenerDispatcher {

    static final FlowAgentListenerDispatcher NONE = new FlowAgentListenerDispatcher(List.of());

    private static final Logger LOG = LoggerFactory.getLogger(FlowAgentListenerDispatcher.class);

    private final List<FlowAgentListener> listeners;

    FlowAgentListenerDispatcher(@All List<FlowAgentListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    /**
     * @return the bean of the running container, or {@link #NONE} when no container is running
     */
    static FlowAgentListenerDispatcher fromContainer() {
        ArcContainer container = Arc.container();
        if ((container == null) || !container.isRunning()) {
            return NONE;
        }
        FlowAgentListenerDispatcher bean = container.instance(FlowAgentListenerDispatcher.class).get();
        return (bean != null) ? bean : NONE;
    }

    void beforeAgentInvocation(AgentRequest request) {
        for (FlowAgentListener listener : listeners) {
            try {
                listener.beforeAgentInvocation(request);
            } catch (RuntimeException e) {
                logFailure(listener, "beforeAgentInvocation", request.agentName(), e);
            }
        }
    }

    void afterAgentInvocation(AgentResponse response) {
        for (FlowAgentListener listener : listeners) {
            try {
                listener.afterAgentInvocation(response);
            } catch (RuntimeException e) {
                logFailure(listener, "afterAgentInvocation", response.agentName(), e);
            }
        }
    }

    void onAgentInvocationError(AgentInvocationError error) {
        for (FlowAgentListener listener : listeners) {
            try {
                listener.onAgentInvocationError(error);
            } catch (RuntimeException e) {
                logFailure(listener, "onAgentInvocationError", error.agentName(), e);
            }
        }
    }

    private static void logFailure(FlowAgentListener listener, String callback, String agentName, RuntimeException e) {
        LOG.warn("{}.{} failed for agent {}", listener.getClass().getName(), callback, agentName, e);
    }
}
