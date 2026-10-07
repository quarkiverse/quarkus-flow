package io.quarkiverse.flow.internal;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Carries thread-bound context (for example the OpenTelemetry {@code Context}) across the thread hops Quarkus Flow makes
 * internally, such as starting a workflow instance asynchronously or handing an agent invocation from a workflow task to
 * the LangChain4j planner.
 * <p>
 * Implementations are discovered with {@link ServiceLoader}; integrations such as {@code quarkus-flow-opentelemetry}
 * register one in {@code META-INF/services}. When none is present, {@link #current()} returns a no-op propagator, so
 * callers never need to depend on a specific context library.
 */
public interface FlowContextPropagator {

    /**
     * Captures the context of the calling thread.
     *
     * @return a snapshot that can later be made current on any thread; never {@code null}
     */
    Snapshot capture();

    /**
     * Context captured by {@link #capture()}.
     */
    @FunctionalInterface
    interface Snapshot {

        Snapshot NONE = () -> Scope.NOOP;

        /**
         * Makes the captured context current on the calling thread until the returned scope is closed.
         */
        Scope activate();
    }

    /**
     * Restores the context that was current before {@link Snapshot#activate()}. Must be closed on the same thread.
     */
    @FunctionalInterface
    interface Scope extends AutoCloseable {

        Scope NOOP = () -> {
        };

        @Override
        void close();
    }

    /**
     * @return the propagator composed of every registered implementation, or a no-op one when none is registered
     */
    static FlowContextPropagator current() {
        return Holder.INSTANCE;
    }

    final class Holder {

        static final FlowContextPropagator INSTANCE = load();

        private Holder() {
        }

        private static FlowContextPropagator load() {
            List<FlowContextPropagator> propagators = ServiceLoader
                    .load(FlowContextPropagator.class, FlowContextPropagator.class.getClassLoader())
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
            return compose(propagators);
        }

        static FlowContextPropagator compose(List<FlowContextPropagator> propagators) {
            if (propagators.isEmpty()) {
                return () -> Snapshot.NONE;
            }
            if (propagators.size() == 1) {
                return propagators.get(0);
            }
            return () -> {
                List<Snapshot> snapshots = propagators.stream().map(FlowContextPropagator::capture).toList();
                return () -> {
                    List<Scope> scopes = snapshots.stream().map(Snapshot::activate).toList();
                    return () -> {
                        for (int i = scopes.size() - 1; i >= 0; i--) {
                            scopes.get(i).close();
                        }
                    };
                };
            };
        }
    }
}
