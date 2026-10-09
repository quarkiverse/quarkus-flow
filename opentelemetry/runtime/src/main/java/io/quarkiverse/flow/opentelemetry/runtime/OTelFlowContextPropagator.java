package io.quarkiverse.flow.opentelemetry.runtime;

import io.opentelemetry.context.Context;
import io.quarkiverse.flow.internal.FlowContextPropagator;

/**
 * Carries the OpenTelemetry {@link Context} across the thread hops Quarkus Flow makes internally, so that work started
 * on another thread (generated agentic sub-workflows, agent invocations) stays in the caller's trace.
 */
public class OTelFlowContextPropagator implements FlowContextPropagator {

    @Override
    public Snapshot capture() {
        Context captured = Context.current();
        return () -> captured.makeCurrent()::close;
    }
}
