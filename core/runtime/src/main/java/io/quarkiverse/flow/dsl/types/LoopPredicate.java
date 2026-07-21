package io.quarkiverse.flow.dsl.types;

import java.util.function.BiPredicate;

@FunctionalInterface
public interface LoopPredicate<T, V> extends FunctionObject, BiPredicate<T, V> {
}
