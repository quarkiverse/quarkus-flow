package io.quarkiverse.flow.dsl.executors;

import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.executors.DefaultTaskExecutorFactory;
import io.serverlessworkflow.impl.executors.TaskExecutorBuilder;

public class JavaTaskExecutorFactory extends DefaultTaskExecutorFactory {

    public TaskExecutorBuilder<? extends TaskBase> getTaskExecutor(
            WorkflowMutablePosition position, Task task, WorkflowDefinition definition) {
        if (task.getForTask() != null) {
            return new JavaForExecutorBuilder(position, task.getForTask(), definition);
        } else if (task.getSwitchTask() != null) {
            return new JavaSwitchExecutorBuilder(position, task.getSwitchTask(), definition);
        } else if (task.getListenTask() != null) {
            return new JavaListenExecutorBuilder(position, task.getListenTask(), definition);
        } else {
            return super.getTaskExecutor(position, task, definition);
        }
    }
}
