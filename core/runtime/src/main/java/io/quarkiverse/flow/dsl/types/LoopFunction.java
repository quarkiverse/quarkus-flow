package io.quarkiverse.flow.dsl.types;

import java.util.function.BiFunction;

@FunctionalInterface
public interface LoopFunction<T, V, R> extends FunctionObject, BiFunction<T, V, R> {
}
