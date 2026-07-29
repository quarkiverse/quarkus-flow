package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;

import io.serverlessworkflow.fluent.spec.AbstractEventConsumptionStrategyBuilder;
import io.serverlessworkflow.fluent.spec.EventFilterBuilder;
import io.serverlessworkflow.fluent.spec.dsl.DSL;

/**
 * A schedule event specification returned by {@link FlowDSL#one(String)} that allows
 * chaining {@link #first()} and {@link #envelope()} to control how the CloudEvent
 * collection is unwrapped before the first task receives it.
 *
 * <p>
 * By design, the Serverless Workflow specification always delivers consumed events as a
 * collection — even when only one event is expected (via {@code one()}). Call
 * {@link #first()} to explicitly extract the first element from that collection so
 * downstream tasks receive a single object rather than a single-element array.
 *
 * <p>
 * Typical usage:
 *
 * <pre>{@code
 * // Extract first CE data payload from the collection
 * FlowWorkflowBuilder.workflow("my-flow")
 *         .schedule(on(one("com.example.event").first()))
 *         .tasks(...)
 *         .build();
 *
 * // Extract first full CE envelope from the collection
 * FlowWorkflowBuilder.workflow("my-flow")
 *         .schedule(on(one("com.example.event").envelope().first()))
 *         .tasks(...)
 *         .build();
 * }</pre>
 */
public class FuncScheduleEventSpec implements Consumer<AbstractEventConsumptionStrategyBuilder<?, ?, ?>> {

    private final String eventType;
    private final Consumer<EventFilterBuilder> filter;
    private boolean first;
    private boolean envelope;

    FuncScheduleEventSpec(String eventType) {
        this.eventType = eventType;
        this.filter = null;
    }

    FuncScheduleEventSpec(Consumer<EventFilterBuilder> filter) {
        this.eventType = null;
        this.filter = filter;
    }

    /**
     * Extract the first item from the CloudEvent collection before the first task
     * receives it. The specification always delivers events as a collection — even
     * for {@code one()} which produces a single-element array. This method unwraps
     * that array so the next task receives a single object directly.
     *
     * <p>
     * By default, only the CE {@code data} payload is extracted. Chain with
     * {@link #envelope()} before this call to keep the full CloudEvent envelope instead.
     *
     * @return this spec for chaining
     */
    public FuncScheduleEventSpec first() {
        this.first = true;
        return this;
    }

    /**
     * When combined with {@link #first()}, passes the full CloudEvent envelope
     * (including extensions, source, type, etc.) instead of just the data payload.
     * Users working with the envelope will need CE serializers to access its fields.
     *
     * @return this spec for chaining
     */
    public FuncScheduleEventSpec envelope() {
        this.envelope = true;
        return this;
    }

    public boolean isFirst() {
        return first;
    }

    public boolean isEnvelope() {
        return envelope;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void accept(AbstractEventConsumptionStrategyBuilder<?, ?, ?> builder) {
        Consumer<EventFilterBuilder> eventFilter = eventType != null
                ? DSL.event().type(eventType)
                : filter;
        ((AbstractEventConsumptionStrategyBuilder) builder).one(eventFilter);
    }
}
