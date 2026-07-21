package io.quarkiverse.flow.dsl.types;

import java.util.function.BiFunction;

/**
 * Functions that expect a unique ID injection in runtime, typically an idempotent generated unique
 * id based on the workflow instance id and task name.
 *
 * @param <T> The task payload input
 * @param <R> The task result output
 */
@FunctionalInterface
public interface UniqueIdBiFunction<T, R> extends BiFunction<String, T, R>, FunctionObject {
    R apply(String uniqueId, T object);
}
