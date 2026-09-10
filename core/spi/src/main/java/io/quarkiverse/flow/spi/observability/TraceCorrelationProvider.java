package io.quarkiverse.flow.spi.observability;

import java.util.Optional;

import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

/**
 * Neutral bridge that lets flow-level logging correlate with an active distributed trace
 * without {@code core} depending on any tracing library.
 */
public interface TraceCorrelationProvider {

    /**
     * Returns the trace/span identifiers of the span the tracing integration is currently
     * tracking for the given lifecycle event (the workflow-instance span for workflow events,
     * the task span for task events), or {@link Optional#empty()} when no span is being
     * tracked (tracing disabled, or the event fires outside any span's lifetime).
     */
    Optional<TraceContext> traceContextFor(WorkflowEvent workflowEvent);

    /**
     * Plain-string view of an active span, deliberately free of any tracing-library types so it
     * can be written straight to the SLF4J MDC or a JSON log field. Every component is non-null;
     * {@code parentId} is an empty string when the span has no recorded parent.
     */
    record TraceContext(String traceId, String spanId, String sampled, String parentId) {
    }

    /** Used when no tracing implementation is installed. */
    TraceCorrelationProvider NOOP = ev -> Optional.empty();
}