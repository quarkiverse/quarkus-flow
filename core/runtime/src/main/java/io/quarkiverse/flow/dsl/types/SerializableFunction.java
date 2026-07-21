package io.quarkiverse.flow.dsl.types;

import java.util.function.Function;

/**
 * Alternative to Function for our DSL to discover the input parameter class in runtime via
 * reflection.
 *
 * @param <T>
 * @param <R>
 */
@FunctionalInterface
public interface SerializableFunction<T, R> extends Function<T, R>, FunctionObject {
}
