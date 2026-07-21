package io.quarkiverse.flow.dsl.executors;

import static io.quarkiverse.flow.dsl.executors.JavaFuncUtils.safeObject;

import java.util.Optional;

import io.quarkiverse.flow.dsl.types.LoopFunction;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;

public class JavaLoopFunctionCallExecutor<T, V, R> extends AbstractJavaCallExecutor<T, R> {

    private final LoopFunction<T, V, R> function;
    private final String varName;

    public JavaLoopFunctionCallExecutor(
            LoopFunction<T, V, R> function,
            String varName,
            Optional<Class<T>> inputClass,
            Optional<Class<R>> outputClass) {
        super(inputClass, outputClass);
        this.function = function;
        this.varName = varName;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected R callJavaFunction(WorkflowContext workflowContext, TaskContext taskContext, T input) {
        return function.apply(input, (V) safeObject(taskContext.variables().get(varName)));
    }
}
