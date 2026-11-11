package io.quarkiverse.flow.internal;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Notified when a LangChain4j agent is invoked while a Quarkus Flow task is executing, so that integrations such as
 * {@code quarkus-flow-opentelemetry} can enrich the enclosing task span without {@code quarkus-flow-langchain4j} depending
 * on them directly.
 * <p>
 * Implementations are discovered with {@link ServiceLoader}. When none is present, {@link #current()} returns a no-op
 * listener, so callers never need to depend on a specific observability library.
 */
public interface FlowAgentInvocationListener {

    /**
     * Called every time a LangChain4j agent is invoked.
     *
     * @param agentName the invoked agent's name; never {@code null}
     */
    void onAgentInvoked(String agentName);

    /**
     * @return the listener composed of every registered implementation, or a no-op one when none is registered
     */
    static FlowAgentInvocationListener current() {
        return Holder.INSTANCE;
    }

    final class Holder {

        static final FlowAgentInvocationListener INSTANCE = load();

        private Holder() {
        }

        private static FlowAgentInvocationListener load() {
            List<FlowAgentInvocationListener> listeners = ServiceLoader
                    .load(FlowAgentInvocationListener.class, FlowAgentInvocationListener.class.getClassLoader())
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
            return compose(listeners);
        }

        static FlowAgentInvocationListener compose(List<FlowAgentInvocationListener> listeners) {
            if (listeners.isEmpty()) {
                return agentName -> {
                };
            }
            if (listeners.size() == 1) {
                return listeners.get(0);
            }
            return agentName -> listeners.forEach(listener -> listener.onAgentInvoked(agentName));
        }
    }
}
