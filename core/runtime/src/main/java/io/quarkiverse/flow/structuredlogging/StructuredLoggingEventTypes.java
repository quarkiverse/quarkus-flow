package io.quarkiverse.flow.structuredlogging;

/**
 * Event type constants for structured logging.
 * <p>
 * Based on the Serverless Workflow specification CloudEvent types defined in
 * {@code io.serverlessworkflow.impl.lifecycle.ce.AbstractLifeCyclePublisher}.
 * <p>
 * Format: {@code workflow.{entity}.{action}} aligned with spec's
 * {@code io.serverlessworkflow.{entity}.{action}.v1} but simplified for logging.
 */
public final class StructuredLoggingEventTypes {

    private StructuredLoggingEventTypes() {
        // Constants class
    }

    // Workflow Instance Events
    public static final String WORKFLOW_INSTANCE_STARTED = "workflow.instance.started";
    public static final String WORKFLOW_INSTANCE_COMPLETED = "workflow.instance.completed";
    public static final String WORKFLOW_INSTANCE_FAILED = "workflow.instance.failed";
    public static final String WORKFLOW_INSTANCE_CANCELLED = "workflow.instance.cancelled";
    public static final String WORKFLOW_INSTANCE_SUSPENDED = "workflow.instance.suspended";
    public static final String WORKFLOW_INSTANCE_RESUMED = "workflow.instance.resumed";
    public static final String WORKFLOW_INSTANCE_STATUS_CHANGED = "workflow.instance.status.changed";

    // Task Events
    public static final String WORKFLOW_TASK_STARTED = "workflow.task.started";
    public static final String WORKFLOW_TASK_COMPLETED = "workflow.task.completed";
    public static final String WORKFLOW_TASK_FAILED = "workflow.task.failed";
    public static final String WORKFLOW_TASK_CANCELLED = "workflow.task.cancelled";
    public static final String WORKFLOW_TASK_SUSPENDED = "workflow.task.suspended";
    public static final String WORKFLOW_TASK_RESUMED = "workflow.task.resumed";
    public static final String WORKFLOW_TASK_RETRIED = "workflow.task.retried";
}
