package io.quarkiverse.flow.dsl.types;

@FunctionalInterface
public interface LoopPredicateIndex<T, V> extends FunctionObject {
    boolean test(T model, V item, Integer index);
}
