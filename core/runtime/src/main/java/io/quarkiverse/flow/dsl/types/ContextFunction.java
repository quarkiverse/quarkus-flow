package io.quarkiverse.flow.dsl.types;

import io.serverlessworkflow.impl.WorkflowContextData;

@FunctionalInterface
public interface ContextFunction<T, R> extends FunctionObject {
    R apply(T object, WorkflowContextData workflowContext);
}
