package io.quarkiverse.flow.dsl;

import io.serverlessworkflow.fluent.spec.AbstractEventFilterBuilder;

public class FuncEventFilterBuilder
        extends AbstractEventFilterBuilder<FuncEventFilterBuilder, FuncEventFilterPropertiesBuilder> {

    @Override
    protected FuncEventFilterBuilder self() {
        return this;
    }

    @Override
    protected FuncEventFilterPropertiesBuilder newEventPropertiesBuilder() {
        return new FuncEventFilterPropertiesBuilder();
    }
}
