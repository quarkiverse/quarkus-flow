package io.quarkiverse.flow.structuredlogging;

import java.util.Map;

import io.serverlessworkflow.impl.LifecycleEvents;

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

    // ========================================
    // Filter Key → CloudEvent Type Mapping
    // ========================================
    // Maps user-friendly filter keys to official CloudEvent types for logging

    private static final Map<String, String> FILTER_TO_CLOUDEVENT_TYPE = Map.ofEntries(
            // Workflow Instance Events
            Map.entry(WORKFLOW_INSTANCE_STARTED, LifecycleEvents.WORKFLOW_STARTED),
            Map.entry(WORKFLOW_INSTANCE_COMPLETED, LifecycleEvents.WORKFLOW_COMPLETED),
            Map.entry(WORKFLOW_INSTANCE_FAULTED, LifecycleEvents.WORKFLOW_FAULTED),
            Map.entry(WORKFLOW_INSTANCE_CANCELLED, LifecycleEvents.WORKFLOW_CANCELLED),
            Map.entry(WORKFLOW_INSTANCE_SUSPENDED, LifecycleEvents.WORKFLOW_SUSPENDED),
            Map.entry(WORKFLOW_INSTANCE_RESUMED, LifecycleEvents.WORKFLOW_RESUMED),
            Map.entry(WORKFLOW_INSTANCE_STATUS_CHANGED, LifecycleEvents.WORKFLOW_STATUS_CHANGED),
            // Task Events
            Map.entry(WORKFLOW_TASK_STARTED, LifecycleEvents.TASK_STARTED),
            Map.entry(WORKFLOW_TASK_COMPLETED, LifecycleEvents.TASK_COMPLETED),
            Map.entry(WORKFLOW_TASK_FAULTED, LifecycleEvents.TASK_FAULTED),
            Map.entry(WORKFLOW_TASK_CANCELLED, LifecycleEvents.TASK_CANCELLED),
            Map.entry(WORKFLOW_TASK_SUSPENDED, LifecycleEvents.TASK_SUSPENDED),
            Map.entry(WORKFLOW_TASK_RESUMED, LifecycleEvents.TASK_RESUMED),
            Map.entry(WORKFLOW_TASK_RETRIED, LifecycleEvents.TASK_RETRIED));

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
