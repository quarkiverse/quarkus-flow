package io.quarkiverse.flow.dsl.executors;

import static io.quarkiverse.flow.dsl.executors.JavaFuncUtils.safeObject;

import java.util.Optional;

import io.quarkiverse.flow.dsl.types.LoopFunctionIndex;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;

public class JavaLoopFunctionIndexCallExecutor<T, V, R> extends AbstractJavaCallExecutor<T, R> {

    private final LoopFunctionIndex<T, V, R> function;
    private final String varName;
    private final String indexName;

    public JavaLoopFunctionIndexCallExecutor(
            LoopFunctionIndex<T, V, R> function,
            String varName,
            String indexName,
            Optional<Class<T>> inputClass,
            Optional<Class<R>> outputClass) {
        super(inputClass, outputClass);
        this.function = function;
        this.varName = varName;
        this.indexName = indexName;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected R callJavaFunction(WorkflowContext workflowContext, TaskContext taskContext, T input) {
        return function.apply(
                input,
                (V) safeObject(taskContext.variables().get(varName)),
                (Integer) safeObject(taskContext.variables().get(indexName)));
    }
}
