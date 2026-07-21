package io.quarkiverse.flow.dsl.types;

import io.serverlessworkflow.impl.WorkflowContextData;

@FunctionalInterface
public interface LoopPredicateIndexContext<T, V> extends FunctionObject {
    boolean test(T model, V item, Integer index, WorkflowContextData context);
}
