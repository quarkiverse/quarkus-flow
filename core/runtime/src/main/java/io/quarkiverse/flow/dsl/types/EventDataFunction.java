package io.quarkiverse.flow.dsl.types;

import java.util.Objects;
import java.util.function.Function;

import io.serverlessworkflow.api.types.EventData;

public class EventDataFunction extends EventData {

    public <T, R> EventData withFunction(Function<T, R> value) {
        setObject(value);
        return this;
    }

    public <T, R> EventData withFunction(Function<T, R> value, Class<T> argClass) {
        Objects.requireNonNull(argClass);
        setObject(new TypedFunction<>(value, argClass));
        return this;
    }

    public <T, R> EventData withFunction(FilterFunction<T, R> value) {
        setObject(value);
        return this;
    }

    public <T, R> EventData withFunction(FilterFunction<T, R> value, Class<T> argClass) {
        setObject(new TypedFilterFunction<>(value, argClass));
        return this;
    }

    public <T, R> EventData withFunction(ContextFunction<T, R> value) {
        setObject(value);
        return this;
    }

    public <T, R> EventData withFunction(ContextFunction<T, R> value, Class<T> argClass) {
        setObject(new TypedContextFunction<>(value, argClass));
        return this;
    }
}
