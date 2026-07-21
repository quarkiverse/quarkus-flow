package io.quarkiverse.flow.dsl.types;

import java.util.Objects;
import java.util.function.Predicate;

import io.serverlessworkflow.api.types.EventData;

public class EventDataPredicate extends EventData {

    public <T> EventDataPredicate withPredicate(Predicate<T> predicate) {
        setObject(predicate);
        return this;
    }

    public <T> EventDataPredicate withPredicate(Predicate<T> predicate, Class<T> clazz) {
        Objects.requireNonNull(clazz);
        setObject(new TypedPredicate<>(predicate, clazz));
        return this;
    }

    public <T> EventDataPredicate withPredicate(ContextPredicate<T> predicate) {
        setObject(predicate);
        return this;
    }

    public <T> EventDataPredicate withPredicate(ContextPredicate<T> predicate, Class<T> clazz) {
        Objects.requireNonNull(clazz);
        setObject(new TypedContextPredicate<>(predicate, clazz));
        return this;
    }

    public <T> EventDataPredicate withPredicate(FilterPredicate<T> predicate) {
        setObject(predicate);
        return this;
    }

    public <T> EventDataPredicate withPredicate(FilterPredicate<T> predicate, Class<T> clazz) {
        Objects.requireNonNull(clazz);
        setObject(new TypedFilterPredicate<>(predicate, clazz));
        return this;
    }
}
