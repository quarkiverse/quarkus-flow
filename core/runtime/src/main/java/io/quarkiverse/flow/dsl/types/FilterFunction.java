package io.quarkiverse.flow.dsl.types;

import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;

@FunctionalInterface
public interface FilterFunction<T, R> extends FunctionObject {
    R apply(T object, WorkflowContextData workflowContext, TaskContextData taskContext);
}
