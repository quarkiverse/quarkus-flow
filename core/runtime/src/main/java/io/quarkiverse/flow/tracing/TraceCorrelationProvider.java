package io.quarkiverse.flow.tracing;

import java.util.Optional;

import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

/**
 * Neutral bridge that lets flow-level logging correlate with an active distributed trace
 * without {@code core} depending on any tracing library.
 */
public interface TraceCorrelationProvider {

    String TRACE_ID = "traceId";
    String SPAN_ID = "spanId";
    String SAMPLED_ID = "sampled";
    String PARENT_ID = "parentId";

    /**
     * Returns the trace/span identifiers of the span the tracing integration is currently
     * tracking for the given lifecycle event (the workflow-instance span for workflow events,
     * the task span for task events), or {@link Optional#empty()} when no span is being
     * tracked (tracing disabled, or the event fires outside any span's lifetime).
     */
    Optional<TraceContext> traceContextFor(WorkflowEvent workflowEvent);

    /**
     * Called once, when a consumer resolves this provider (via {@link TraceCorrelationProviders#resolve})
     * in order to call {@link #traceContextFor} for lifecycle events going forward.
     * <p>
     * Lets an implementation that caches trace data per lifecycle event (so it can release an
     * entry once every registered consumer has read it) know how many reads to expect. Default
     * no-op: implementations that don't cache anything (e.g. they always recompute from a live
     * span) have no reason to track this.
     */
    default void registerConsumer() {
    }

    /**
     * How many consumers have registered via {@link #registerConsumer()} so far. Meant to be
     * read by the producer side of an implementation that caches trace data (so it knows how
     * many reads to expect per entry), not by consumers themselves. Default {@code 0}: only
     * implementations that actually track registrations need to override this.
     */
    default int registeredConsumerCount() {
        return 0;
    }

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
