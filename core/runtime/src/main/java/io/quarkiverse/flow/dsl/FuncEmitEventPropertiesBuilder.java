package io.quarkiverse.flow.dsl;

import java.util.function.Function;

import io.cloudevents.CloudEventData;
import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.EventDataFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.serverlessworkflow.fluent.spec.AbstractEventPropertiesBuilder;

public class FuncEmitEventPropertiesBuilder
        extends AbstractEventPropertiesBuilder<FuncEmitEventPropertiesBuilder> {

    @Override
    protected FuncEmitEventPropertiesBuilder self() {
        return this;
    }

    public <T> FuncEmitEventPropertiesBuilder data(SerializableFunction<T, CloudEventData> function) {
        this.eventProperties.setData(new EventDataFunction().withFunction(function));
        return this;
    }

    public <T> FuncEmitEventPropertiesBuilder data(
            Function<T, CloudEventData> function, Class<T> clazz) {
        this.eventProperties.setData(new EventDataFunction().withFunction(function, clazz));
        return this;
    }

    public <T> FuncEmitEventPropertiesBuilder data(
            ContextFunction<T, CloudEventData> function, Class<T> clazz) {
        this.eventProperties.setData(new EventDataFunction().withFunction(function, clazz));
        return this;
    }

    public <T> FuncEmitEventPropertiesBuilder data(
            FilterFunction<T, CloudEventData> function, Class<T> clazz) {
        this.eventProperties.setData(new EventDataFunction().withFunction(function, clazz));
        return this;
    }
}
