package io.quarkiverse.flow.dsl.types;

import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;

@FunctionalInterface
public interface FilterPredicate<T> extends FunctionObject {
    boolean test(T value, WorkflowContextData workflow, TaskContextData task);
}
