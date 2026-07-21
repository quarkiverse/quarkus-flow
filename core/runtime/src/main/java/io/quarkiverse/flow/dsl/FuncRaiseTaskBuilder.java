package io.quarkiverse.flow.dsl;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.serverlessworkflow.fluent.spec.BaseRaiseTaskBuilder;

public class FuncRaiseTaskBuilder extends BaseRaiseTaskBuilder<FuncRaiseTaskBuilder>
        implements FuncTaskTransformations<FuncRaiseTaskBuilder>,
        ConditionalTaskBuilder<FuncRaiseTaskBuilder> {

    FuncRaiseTaskBuilder() {
        super();
    }

    @Override
    protected FuncRaiseTaskBuilder self() {
        return this;
    }
}
