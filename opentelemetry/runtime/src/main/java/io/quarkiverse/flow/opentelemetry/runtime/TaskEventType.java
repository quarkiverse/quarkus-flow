package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.NoSuchElementException;

import io.serverlessworkflow.impl.LifecycleEvents;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

public enum TaskEventType {
    TASK_STARTED(LifecycleEvents.TASK_STARTED),
    TASK_SUSPENDED(LifecycleEvents.TASK_SUSPENDED),
    TASK_RESUMED(LifecycleEvents.TASK_RESUMED),
    TASK_COMPLETED(LifecycleEvents.TASK_COMPLETED),
    TASK_CANCELLED(LifecycleEvents.TASK_CANCELLED),
    TASK_FAILED(LifecycleEvents.TASK_FAULTED),
    TASK_RETRIED(LifecycleEvents.TASK_RETRIED);

    private final String id;

    TaskEventType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static TaskEventType fromEvent(WorkflowEvent event) {
        if (event instanceof TaskStartedEvent) {
            return TASK_STARTED;
        }
        if (event instanceof TaskSuspendedEvent) {
            return TASK_SUSPENDED;
        }
        if (event instanceof TaskResumedEvent) {
            return TASK_RESUMED;
        }
        if (event instanceof TaskCompletedEvent) {
            return TASK_COMPLETED;
        }
        if (event instanceof TaskCancelledEvent) {
            return TASK_CANCELLED;
        }
        if (event instanceof TaskFailedEvent) {
            return TASK_FAILED;
        }
        if (event instanceof TaskRetriedEvent) {
            return TASK_RETRIED;
        }
        throw new NoSuchElementException("TaskEvent: " + event.getClass() + " is not recognized.");
    }
}
