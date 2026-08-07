package io.quarkiverse.flow.config;

import java.util.Objects;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Centralized naming conventions for client configuration keys.
 * <p>
 * Implements the unified client naming pattern from ADR 2026-07-07.
 * Used by HTTP, gRPC, and OIDC modules for consistent key generation.
 * <p>
 * <b>Key format:</b> {@code ":"} separates namespace/name/version,
 * {@code ".task."} separates the workflow identity from the task name.
 */
public final class ClientNamingConvention {

    static final String SEPARATOR = ":";
    static final String TASK_SEGMENT = ".task.";

    private ClientNamingConvention() {
    }

    /**
     * Full task-level key: {@code namespace:name:version.task.taskName}.
     */
    public static String taskKeyFull(WorkflowDefinitionId workflowId, String taskName) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        Objects.requireNonNull(taskName, "taskName is null");
        return workflowId.toString(SEPARATOR) + TASK_SEGMENT + taskName;
    }

    /**
     * Medium task-level key (no version): {@code namespace:name.task.taskName}.
     */
    public static String taskKeyMedium(WorkflowDefinitionId workflowId, String taskName) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        Objects.requireNonNull(taskName, "taskName is null");
        return workflowId.namespace() + SEPARATOR + workflowId.name() + TASK_SEGMENT + taskName;
    }

    /**
     * Short task-level key (name only): {@code name.task.taskName}.
     */
    public static String taskKeyShort(WorkflowDefinitionId workflowId, String taskName) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        Objects.requireNonNull(taskName, "taskName is null");
        return workflowId.name() + TASK_SEGMENT + taskName;
    }

    /**
     * Full workflow-level key: {@code namespace:name:version}.
     */
    public static String workflowKeyFull(WorkflowDefinitionId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        return workflowId.toString(SEPARATOR);
    }

    /**
     * Medium workflow-level key (no version): {@code namespace:name}.
     */
    public static String workflowKeyMedium(WorkflowDefinitionId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        return workflowId.namespace() + SEPARATOR + workflowId.name();
    }

    /**
     * Short workflow-level key (name only): {@code name}.
     */
    public static String workflowKeyShort(WorkflowDefinitionId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        return workflowId.name();
    }
}
