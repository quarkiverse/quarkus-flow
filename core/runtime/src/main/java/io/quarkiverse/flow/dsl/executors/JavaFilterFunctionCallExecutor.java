package io.quarkiverse.flow.dsl.executors;

import java.util.Optional;

import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;

public class JavaFilterFunctionCallExecutor<T, V> extends AbstractJavaCallExecutor<T, V> {

    private final FilterFunction<T, V> function;

    public JavaFilterFunctionCallExecutor(
            Optional<Class<T>> inputClass,
            Optional<Class<V>> outputClass,
            FilterFunction<T, V> function) {
        super(inputClass, outputClass);
        this.function = function;
    }

    @Override
    protected V callJavaFunction(WorkflowContext workflowContext, TaskContext taskContext, T input) {
        return function.apply(input, workflowContext, taskContext);
    }
}
