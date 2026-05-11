package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.NoSuchElementException;

import io.serverlessworkflow.impl.LifecycleEvents;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

public enum WorkflowEventType {
    WORKFLOW_STARTED(LifecycleEvents.WORKFLOW_STARTED),
    WORKFLOW_SUSPENDED(LifecycleEvents.WORKFLOW_SUSPENDED),
    WORKFLOW_RESUMED(LifecycleEvents.WORKFLOW_RESUMED),
    WORKFLOW_COMPLETED(LifecycleEvents.WORKFLOW_COMPLETED),
    WORKFLOW_CANCELLED(LifecycleEvents.WORKFLOW_CANCELLED),
    WORKFLOW_FAILED(LifecycleEvents.WORKFLOW_FAULTED);

    private final String id;

    WorkflowEventType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static WorkflowEventType fromEvent(WorkflowEvent event) {
        if (event instanceof WorkflowStartedEvent) {
            return WORKFLOW_STARTED;
        }
        if (event instanceof WorkflowSuspendedEvent) {
            return WORKFLOW_SUSPENDED;
        }
        if (event instanceof WorkflowResumedEvent) {
            return WORKFLOW_RESUMED;
        }
        if (event instanceof WorkflowCompletedEvent) {
            return WORKFLOW_COMPLETED;
        }
        if (event instanceof WorkflowCancelledEvent) {
            return WORKFLOW_CANCELLED;
        }
        if (event instanceof WorkflowFailedEvent) {
            return WORKFLOW_FAILED;
        }
        throw new NoSuchElementException("WorkflowEvent: " + event.getClass() + " is not recognized.");
    }
}
