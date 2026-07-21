package io.quarkiverse.flow.dsl.types;

import io.serverlessworkflow.impl.WorkflowContextData;

@FunctionalInterface
public interface ContextPredicate<T> extends FunctionObject {
    boolean test(T value, WorkflowContextData context);
}
