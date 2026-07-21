package io.quarkiverse.flow.dsl.spi;

import java.util.Objects;
import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.types.TypedPredicate;
import io.serverlessworkflow.api.types.TaskBase;

public interface ConditionalTaskBuilder<SELF> {

    TaskBase getTask();

    @SuppressWarnings("unchecked")
    default SELF when(Predicate<?> predicate) {
        ConditionalTaskBuilderHelper.setMetadata(getTask(), predicate);
        return (SELF) this;
    }

    @SuppressWarnings("unchecked")
    default <T> SELF when(Predicate<T> predicate, Class<T> argClass) {
        Objects.requireNonNull(argClass);
        ConditionalTaskBuilderHelper.setMetadata(getTask(), new TypedPredicate<>(predicate, argClass));
        return (SELF) this;
    }
}
