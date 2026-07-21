package io.quarkiverse.flow.dsl.executors;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.executors.CallableTask;

public class JavaConsumerCallExecutor<T> implements CallableTask {

    private final Optional<Class<T>> inputClass;
    private final Consumer<T> consumer;

    public JavaConsumerCallExecutor(Optional<Class<T>> inputClass, Consumer<T> consumer) {
        this.inputClass = inputClass;
        this.consumer = consumer;
    }

    @Override
    public CompletableFuture<WorkflowModel> apply(
            WorkflowContext workflowContext, TaskContext taskContext, WorkflowModel input) {
        T typed = JavaFuncUtils.convertT(input, inputClass);
        consumer.accept(typed);
        return CompletableFuture.completedFuture(input);
    }
}
