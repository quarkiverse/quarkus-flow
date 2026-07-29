package io.quarkiverse.flow.dsl;

import static io.serverlessworkflow.types.Defaults.DEFAULT_NAMESPACE;
import static io.serverlessworkflow.types.Defaults.DEFAULT_VERSION;

import java.util.UUID;
import java.util.function.Consumer;

import io.cloudevents.CloudEvent;
import io.quarkiverse.flow.dsl.spi.FuncTransformations;
import io.serverlessworkflow.fluent.spec.BaseWorkflowBuilder;
import io.serverlessworkflow.fluent.spec.ScheduleBuilder;

public class FlowWorkflowBuilder
        extends BaseWorkflowBuilder<FlowWorkflowBuilder, FuncDoTaskBuilder, FuncTaskItemListBuilder>
        implements FuncTransformations<FlowWorkflowBuilder> {

    protected FlowWorkflowBuilder(final String name, final String namespace, final String version) {
        super(name, namespace, version);
    }

    /**
     * Schedule this workflow using a {@link FuncScheduleSpec} that may carry
     * {@link FuncScheduleEventSpec#first()} and {@link FuncScheduleEventSpec#envelope()}
     * flags. When {@code first()} is set, an {@code inputFrom} filter is automatically
     * applied to extract the first item from the CloudEvent collection before the first
     * task receives it.
     *
     * <p>
     * Usage:
     *
     * <pre>{@code
     * import static io.quarkiverse.flow.dsl.FlowDSL.*;
     *
     * // Extract first CE data payload
     * FlowWorkflowBuilder.workflow("my-flow")
     *         .schedule(on(one("com.example.event").first()))
     *         .tasks(...)
     *         .build();
     *
     * // Extract first full CE envelope
     * FlowWorkflowBuilder.workflow("my-flow")
     *         .schedule(on(one("com.example.event").envelope().first()))
     *         .tasks(...)
     *         .build();
     * }</pre>
     *
     * @param spec the schedule spec (from {@code on(one(...))})
     * @return this builder for chaining
     */
    public FlowWorkflowBuilder schedule(FuncScheduleSpec spec) {
        schedule((Consumer<ScheduleBuilder>) spec);
        if (spec.isFirst()) {
            if (spec.isEnvelope()) {
                inputFrom(ce -> ce);
            } else {
                inputFrom(CloudEvent::getData);
            }
        }
        return this;
    }

    public static FlowWorkflowBuilder workflow(
            final String name, final String namespace, final String version) {
        return new FlowWorkflowBuilder(name, namespace, version);
    }

    public static FlowWorkflowBuilder workflow(final String name, final String namespace) {
        return new FlowWorkflowBuilder(name, namespace, DEFAULT_VERSION);
    }

    public static FlowWorkflowBuilder workflow(final String name) {
        return new FlowWorkflowBuilder(name, DEFAULT_NAMESPACE, DEFAULT_VERSION);
    }

    public static FlowWorkflowBuilder workflow() {
        return new FlowWorkflowBuilder(
                UUID.randomUUID().toString(), DEFAULT_NAMESPACE, DEFAULT_VERSION);
    }

    @Override
    protected FuncDoTaskBuilder newDo(int listSizeOffset) {
        return new FuncDoTaskBuilder(listSizeOffset);
    }

    @Override
    protected FlowWorkflowBuilder self() {
        return this;
    }
}
