package io.quarkiverse.flow.opentelemetry.runtime;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;

public record TaskEventInfo(
        String wfApplicationId,
        String wfNamespace,
        String wfName,
        String wfVersion,
        String wfInstanceId,
        TaskEventType eventType,
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
        var document = definition.workflow().getDocument();
        var taskContext = ev.taskContext();

        return new TaskEventInfo(
                definition.application().id(),
                definition.id().namespace(),
                document.getName(),
                document.getVersion(),
                context.instanceData().id(),
                TaskEventType.fromEvent(ev),
                TaskType.fromTask(ev.taskContext().task()),
                taskContext.position().jsonPointer(),
                taskContext.taskName(),
                taskContext.iteration(),
                ((TaskContext) ev.taskContext()).isRetrying(),
                ev.taskContext().retryAttempt(),
                ((TaskContext) ev.taskContext()).tryRetryCount().orElse(0));

    }
}
