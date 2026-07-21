package io.quarkiverse.flow.dsl;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.serverlessworkflow.fluent.spec.AbstractEmitTaskBuilder;

public class FuncEmitTaskBuilder
        extends AbstractEmitTaskBuilder<FuncEmitTaskBuilder, FuncEmitEventPropertiesBuilder>
        implements ConditionalTaskBuilder<FuncEmitTaskBuilder>,
        FuncTaskTransformations<FuncEmitTaskBuilder> {
    FuncEmitTaskBuilder() {
        super();
    }

    @Override
    protected FuncEmitTaskBuilder self() {
        return this;
    }

    @Override
    protected FuncEmitEventPropertiesBuilder newEventPropertiesBuilder() {
        return new FuncEmitEventPropertiesBuilder();
    }
}
