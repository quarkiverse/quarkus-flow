package io.quarkiverse.flow.tracing;

import java.util.Optional;

import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

/**
 * Neutral bridge that lets flow-level logging correlate with an active distributed trace
 * without {@code core} depending on any tracing library.
 * <p>
 * An implementation is contributed by the {@code quarkus-flow-opentelemetry} extension when
 * OpenTelemetry tracing is enabled. When that extension is absent the {@link #NOOP} instance
 * is used and flow logs simply carry no trace identifiers.
 */
public interface TraceCorrelationProvider {

    /**
     * Returns the trace/span identifiers of the span the tracing integration is currently
     * tracking for the given lifecycle event (the workflow-instance span for workflow events,
     * the task span for task events), or {@link Optional#empty()} when no span is being
     * tracked (tracing disabled, or the event fires outside any span's lifetime).
     */
    Optional<TraceContext> traceContextFor(WorkflowEvent ev);

    /**
     * Plain-string view of an active span, deliberately free of any tracing-library types.
     */
    record TraceContext(String traceId, String spanId, String sampled, String parentId) {
    }

    /** Used when no tracing implementation is installed. */
    TraceCorrelationProvider NOOP = ev -> Optional.empty();
}