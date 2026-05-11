package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.NoSuchElementException;

import io.opentelemetry.api.trace.Span;

public class SpanUtils {
    private static final String WORKFLOW_EXECUTE_ACTION = "workflow.execute";
    private static final String TASK_EXECUTE_ACTION = "task.execute";

    private SpanUtils() {
    }

    public enum TaskNameStrategy {
        ACTION_AND_TASK_NAME,
        ACTION_AND_TASK_ID,
        DEBUG
    }

    public static void appendWorkflowEvent(Span span, WorkflowEventType eventType) {
        span.addEvent(eventType.id());
    }

    public static void appendTaskEvent(Span span, TaskEventType eventType) {
        span.addEvent(eventType.id());
    }

    public static String generateTaskSpanName(
            TaskNameStrategy nameStrategy,
            String taskInstanceId,
            String taskName,
            int taskInstanceIteration, int retryAttempt) {
        switch (nameStrategy) {
            case ACTION_AND_TASK_NAME:
                return TASK_EXECUTE_ACTION + " " + taskName;
            case ACTION_AND_TASK_ID:
                return TASK_EXECUTE_ACTION + " " + taskInstanceId;
            case DEBUG:
                return taskInstanceId + "-" + " (" + taskName + ") # " + taskInstanceIteration + "(retry: " + retryAttempt
                        + ")";
            default:
                throw new NoSuchElementException("TaskNameStrategy " + nameStrategy + "is not recognized.");
        }
    }

    public static String generateWorkflowSpanName(String workflowName) {
        return WORKFLOW_EXECUTE_ACTION + " " + workflowName;
    }
}
