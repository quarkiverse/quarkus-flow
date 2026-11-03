package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * Holds a Flow task's {@code task.execute} span, together with the first-wins claim used to type it as a GenAI
 * {@code invoke_agent} operation when a LangChain4j agent is invoked from the task body at runtime.
 * <p>
 * Mutable and shared across threads: {@code FlowPlanner} invokes agents on a different thread than the workflow task.
 */
final class FlowTaskSpanRef {

    static final ContextKey<FlowTaskSpanRef> KEY = ContextKey.named("flow-task-span-ref");

    private static final String ALREADY_TYPED = "";

    private final Span span;
    private final AtomicReference<String> claimedBy;

    FlowTaskSpanRef(Span span) {
        this(span, false);
    }

    /**
     * @param alreadyTyped whether the span was already typed as an {@code invoke_agent} operation at build time, for
     *        example by {@code SpanUtils#enrichAgentCall}; when {@code true}, the first-wins claim is pre-seeded so
     *        runtime detection only adds span events and never overwrites the existing attributes
     */
    FlowTaskSpanRef(Span span, boolean alreadyTyped) {
        this.span = span;
        this.claimedBy = new AtomicReference<>(alreadyTyped ? ALREADY_TYPED : null);
    }

    Span span() {
        return span;
    }

    /**
     * @return {@code true} only for the first caller, including when the span was already typed at build time
     */
    boolean claim(String agentName) {
        return claimedBy.compareAndSet(null, agentName);
    }

    static Optional<FlowTaskSpanRef> of(Context context) {
        return Optional.ofNullable(context.get(KEY));
    }
}
