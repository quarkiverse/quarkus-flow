package io.quarkiverse.flow.testing.events;

/**
 * Enumeration of all workflow lifecycle event types that can be recorded during test execution.
 */
public enum EventType {
    /**
     * Workflow instance has started execution.
     */
    WORKFLOW_STARTED,

    /**
     * Workflow instance has completed successfully.
     */
    WORKFLOW_COMPLETED,

    /**
     * Workflow instance has failed with an error.
     */
    WORKFLOW_FAILED,

    /**
     * Workflow instance has been cancelled.
     */
    WORKFLOW_CANCELLED,

    /**
     * Workflow instance has been suspended.
     */
    WORKFLOW_SUSPENDED,

    /**
     * Workflow instance has been resumed from suspension.
     */
    WORKFLOW_RESUMED,

    /**
     * Workflow instance status has changed.
     */
    WORKFLOW_STATUS_CHANGED,

    /**
     * A task within the workflow has started execution.
     */
    TASK_STARTED,

    /**
     * A task within the workflow has completed successfully.
     */
    TASK_COMPLETED,

    /**
     * A task within the workflow has failed with an error.
     */
    TASK_FAILED,

    /**
     * A task within the workflow has been cancelled.
     */
    TASK_CANCELLED,

    /**
     * A task within the workflow has been suspended.
     */
    TASK_SUSPENDED,

    /**
     * A task within the workflow has been resumed from suspension.
     */
    TASK_RESUMED,

    /**
     * A task within the workflow has been retried after a failure.
     */
    TASK_RETRIED
}
