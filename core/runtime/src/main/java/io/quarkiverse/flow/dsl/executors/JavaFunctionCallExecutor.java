package io.quarkiverse.flow.dsl.executors;

import java.util.Optional;
import java.util.function.Function;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;

public class JavaFunctionCallExecutor<T, V> extends AbstractJavaCallExecutor<T, V> {

    private final Function<T, V> function;

    public JavaFunctionCallExecutor(
            Optional<Class<T>> inputClass, Optional<Class<V>> outputClass, Function<T, V> function) {
        super(inputClass, outputClass);
        this.function = function;
    }

    @Override
    protected V callJavaFunction(WorkflowContext workflowContext, TaskContext taskContext, T input) {
        return function.apply(input);
    }
}
