package io.quarkiverse.flow.opentelemetry.runtime;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.lifecycle.EventType;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;

public record TaskEventInfo(
        String wfApplicationId,
        String wfNamespace,
        String wfName,
        String wfVersion,
        String wfInstanceId,
        EventType eventType,
        TaskType taskType,
        String taskId,
        String taskName,
        int taskInstanceIteration,
        boolean taskInstanceRetrying,
        int taskInstanceRetryAttempt,
        int taskInstanceRetryCount

) {
    public static TaskEventInfo from(TaskEvent ev) {
        var context = ev.workflowContext();
        var definition = context.definition();
        var taskContext = ev.taskContext();

        return new TaskEventInfo(
                definition.application().id(),
                definition.id().namespace(),
                definition.id().name(),
                definition.id().version(),
                context.instanceData().id(),
                ev.type(),
                TaskType.fromTask(ev.taskContext().task()),
                taskContext.position().jsonPointer(),
                taskContext.taskName(),
                taskContext.iteration(),
                ((TaskContext) taskContext).isRetrying(),
                taskContext.retryAttempt(),
                ((TaskContext) taskContext).tryRetryCount().orElse(0));

    }
}
