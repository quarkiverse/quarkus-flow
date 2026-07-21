package io.quarkiverse.flow.dsl.types;

import java.util.function.Predicate;

@FunctionalInterface
public interface SerializablePredicate<T> extends Predicate<T>, FunctionObject {
}
