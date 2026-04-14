package io.quarkiverse.flow.structuredlogging;

import java.util.Map;

/**
 * Event type constants for structured logging.
 * <p>
 * This class serves two purposes:
 * <ul>
 * <li><b>Configuration filtering keys</b>: Simplified, user-friendly event type names for
 * configuring which events to log (e.g., {@code workflow.instance.started})
 * <li><b>CloudEvent type mapping</b>: Maps filter keys to official Serverless Workflow
 * specification CloudEvent types (e.g., {@code io.serverlessworkflow.workflow.started.v1})
 * </ul>
 * <p>
 * The official CloudEvent types are defined in the Serverless Workflow SDK:
 * {@code io.serverlessworkflow.impl.lifecycle.ce.AbstractLifeCyclePublisher}
 * <p>
 * <b>Note:</b> The CloudEvent type constants are temporarily copied here because they are
 * private in the SDK. See: https://github.com/serverlessworkflow/sdk-java/issues/1314
 * Once made public in the SDK, we should reference them directly instead.
 */
final class StructuredLoggingEventTypes {

    private StructuredLoggingEventTypes() {
        // Constants class
    }

    // ========================================
    // Filter Keys (for configuration)
    // ========================================
    // These simplified event types are used for user configuration and filtering

    // Workflow Instance Events
    static final String WORKFLOW_INSTANCE_STARTED = "workflow.instance.started";
    static final String WORKFLOW_INSTANCE_COMPLETED = "workflow.instance.completed";
    static final String WORKFLOW_INSTANCE_FAULTED = "workflow.instance.faulted";
    static final String WORKFLOW_INSTANCE_CANCELLED = "workflow.instance.cancelled";
    static final String WORKFLOW_INSTANCE_SUSPENDED = "workflow.instance.suspended";
    static final String WORKFLOW_INSTANCE_RESUMED = "workflow.instance.resumed";
    static final String WORKFLOW_INSTANCE_STATUS_CHANGED = "workflow.instance.status.changed";

    // Task Events
    static final String WORKFLOW_TASK_STARTED = "workflow.task.started";
    static final String WORKFLOW_TASK_COMPLETED = "workflow.task.completed";
    static final String WORKFLOW_TASK_FAULTED = "workflow.task.faulted";
    static final String WORKFLOW_TASK_CANCELLED = "workflow.task.cancelled";
    static final String WORKFLOW_TASK_SUSPENDED = "workflow.task.suspended";
    static final String WORKFLOW_TASK_RESUMED = "workflow.task.resumed";
    static final String WORKFLOW_TASK_RETRIED = "workflow.task.retried";

    // ========================================
    // Official Serverless Workflow CloudEvent Types
    // ========================================
    // These are the official CloudEvent types from the SW specification
    // Source: io.serverlessworkflow.impl.lifecycle.ce.AbstractLifeCyclePublisher
    // TODO: Replace with direct references once https://github.com/serverlessworkflow/sdk-java/issues/1314 is resolved

    // Task CloudEvent Types
    private static final String CE_TASK_STARTED = "io.serverlessworkflow.task.started.v1";
    private static final String CE_TASK_COMPLETED = "io.serverlessworkflow.task.completed.v1";
    private static final String CE_TASK_SUSPENDED = "io.serverlessworkflow.task.suspended.v1";
    private static final String CE_TASK_RESUMED = "io.serverlessworkflow.task.resumed.v1";
    private static final String CE_TASK_FAULTED = "io.serverlessworkflow.task.faulted.v1";
    private static final String CE_TASK_CANCELLED = "io.serverlessworkflow.task.cancelled.v1";
    private static final String CE_TASK_RETRIED = "io.serverlessworkflow.task.retried.v1";

    // Workflow CloudEvent Types
    private static final String CE_WORKFLOW_STARTED = "io.serverlessworkflow.workflow.started.v1";
    private static final String CE_WORKFLOW_COMPLETED = "io.serverlessworkflow.workflow.completed.v1";
    private static final String CE_WORKFLOW_SUSPENDED = "io.serverlessworkflow.workflow.suspended.v1";
    private static final String CE_WORKFLOW_RESUMED = "io.serverlessworkflow.workflow.resumed.v1";
    private static final String CE_WORKFLOW_FAULTED = "io.serverlessworkflow.workflow.faulted.v1";
    private static final String CE_WORKFLOW_CANCELLED = "io.serverlessworkflow.workflow.cancelled.v1";
    private static final String CE_WORKFLOW_STATUS_CHANGED = "io.serverlessworkflow.workflow.status-changed.v1";

    // ========================================
    // Filter Key → CloudEvent Type Mapping
    // ========================================
    // Maps user-friendly filter keys to official CloudEvent types for logging

    private static final Map<String, String> FILTER_TO_CLOUDEVENT_TYPE = Map.ofEntries(
            // Workflow Instance Events
            Map.entry(WORKFLOW_INSTANCE_STARTED, CE_WORKFLOW_STARTED),
            Map.entry(WORKFLOW_INSTANCE_COMPLETED, CE_WORKFLOW_COMPLETED),
            Map.entry(WORKFLOW_INSTANCE_FAULTED, CE_WORKFLOW_FAULTED),
            Map.entry(WORKFLOW_INSTANCE_CANCELLED, CE_WORKFLOW_CANCELLED),
            Map.entry(WORKFLOW_INSTANCE_SUSPENDED, CE_WORKFLOW_SUSPENDED),
            Map.entry(WORKFLOW_INSTANCE_RESUMED, CE_WORKFLOW_RESUMED),
            Map.entry(WORKFLOW_INSTANCE_STATUS_CHANGED, CE_WORKFLOW_STATUS_CHANGED),
            // Task Events
            Map.entry(WORKFLOW_TASK_STARTED, CE_TASK_STARTED),
            Map.entry(WORKFLOW_TASK_COMPLETED, CE_TASK_COMPLETED),
            Map.entry(WORKFLOW_TASK_FAULTED, CE_TASK_FAULTED),
            Map.entry(WORKFLOW_TASK_CANCELLED, CE_TASK_CANCELLED),
            Map.entry(WORKFLOW_TASK_SUSPENDED, CE_TASK_SUSPENDED),
            Map.entry(WORKFLOW_TASK_RESUMED, CE_TASK_RESUMED),
            Map.entry(WORKFLOW_TASK_RETRIED, CE_TASK_RETRIED));

    /**
     * Maps a filter key (simplified event type) to the official Serverless Workflow CloudEvent type.
     *
     * @param filterKey the simplified event type used for filtering (e.g., "workflow.instance.started")
     * @return the official CloudEvent type (e.g., "io.serverlessworkflow.workflow.started.v1")
     */
    static String toCloudEventType(String filterKey) {
        String cloudEventType = FILTER_TO_CLOUDEVENT_TYPE.get(filterKey);
        if (cloudEventType == null) {
            // Fallback: return the filter key itself (defensive programming)
            return filterKey;
        }
        return cloudEventType;
    }
}
