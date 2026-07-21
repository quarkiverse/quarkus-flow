package io.quarkiverse.flow.dsl.types;

import java.util.Objects;
import java.util.function.Predicate;

import io.serverlessworkflow.api.types.EventProperties;

public class EventPropertiesPredicate extends EventProperties {

    private Object filterPredicate;

    public Object getFilterPredicate() {
        return filterPredicate;
    }

    public <T> EventPropertiesPredicate withPredicate(Predicate<T> predicate) {
        this.filterPredicate = predicate;
        return this;
    }

    public <T> EventPropertiesPredicate withPredicate(Predicate<T> predicate, Class<T> clazz) {
        Objects.requireNonNull(clazz);
        this.filterPredicate = new TypedPredicate<>(predicate, clazz);
        return this;
    }

    public <T> EventPropertiesPredicate withPredicate(ContextPredicate<T> predicate) {
        this.filterPredicate = predicate;
        return this;
    }

    public <T> EventPropertiesPredicate withPredicate(ContextPredicate<T> predicate, Class<T> clazz) {
        Objects.requireNonNull(clazz);
        this.filterPredicate = new TypedContextPredicate<>(predicate, clazz);
        return this;
    }

    public <T> EventPropertiesPredicate withPredicate(FilterPredicate<T> predicate) {
        this.filterPredicate = predicate;
        return this;
    }

    public <T> EventPropertiesPredicate withPredicate(FilterPredicate<T> predicate, Class<T> clazz) {
        Objects.requireNonNull(clazz);
        this.filterPredicate = new TypedFilterPredicate<>(predicate, clazz);
        return this;
    }
}
