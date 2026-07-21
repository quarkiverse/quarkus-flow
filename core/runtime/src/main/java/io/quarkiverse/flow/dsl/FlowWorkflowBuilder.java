package io.quarkiverse.flow.dsl;

import static io.serverlessworkflow.types.Defaults.DEFAULT_NAMESPACE;
import static io.serverlessworkflow.types.Defaults.DEFAULT_VERSION;

import java.util.UUID;

import io.quarkiverse.flow.dsl.spi.FuncTransformations;
import io.serverlessworkflow.fluent.spec.BaseWorkflowBuilder;

public class FlowWorkflowBuilder
        extends BaseWorkflowBuilder<FlowWorkflowBuilder, FuncDoTaskBuilder, FuncTaskItemListBuilder>
        implements FuncTransformations<FlowWorkflowBuilder> {

    protected FlowWorkflowBuilder(final String name, final String namespace, final String version) {
        super(name, namespace, version);
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
