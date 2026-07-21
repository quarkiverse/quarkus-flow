package io.quarkiverse.flow.dsl.types;

import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;

@FunctionalInterface
public interface LoopPredicateIndexFilter<T, V> extends FunctionObject {
    boolean test(T model, V item, Integer index, WorkflowContextData workflow, TaskContextData task);
}
