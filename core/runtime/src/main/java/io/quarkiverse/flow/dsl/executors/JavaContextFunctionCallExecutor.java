package io.quarkiverse.flow.dsl.executors;

import java.util.Optional;

import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;

public class JavaContextFunctionCallExecutor<T, V> extends AbstractJavaCallExecutor<T, V> {

    private final ContextFunction<T, V> function;

    public JavaContextFunctionCallExecutor(
            Optional<Class<T>> inputClass,
            Optional<Class<V>> outputClass,
            ContextFunction<T, V> function) {
        super(inputClass, outputClass);
        this.function = function;
    }

    @Override
    protected V callJavaFunction(WorkflowContext workflowContext, TaskContext taskContext, T input) {
        return function.apply(input, workflowContext);
    }
}
