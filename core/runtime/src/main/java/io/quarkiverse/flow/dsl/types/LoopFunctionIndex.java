package io.quarkiverse.flow.dsl.types;

@FunctionalInterface
public interface LoopFunctionIndex<T, V, R> extends FunctionObject {
    R apply(T model, V item, Integer index);
}
