package io.quarkiverse.flow.oidc;

import java.util.Objects;

import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Centralized naming conventions for OIDC client names and configuration keys.
 * <p>
 * Implements the unified client naming pattern from ADR 2026-07-07.
 * <p>
 * <b>Client names</b> (internal storage): {@code namespace:name:version.task.taskName}
 * <br>
 * <b>Config keys</b> (user-facing routing): Progressive specificity from short to full
 */
public final class OidcNamingConvention {

    private static final String SEPARATOR = ":";
    private static final String TASK_SEGMENT = ".task.";

    private OidcNamingConvention() {
    }

    // ========== Client Names & Full Config Keys ==========

    /**
     * Generates full OIDC client name for inline task authentication.
     * <p>
     * This is also the full task-level config key per ADR 2026-07-07.
     * <p>
     * Format: {@code namespace:name:version.task.taskName}
     * <p>
     * Example: {@code acme:orders:1.0.0.task.payment}
     *
     * @param workflow the workflow
     * @param taskItem the task item
     * @return composite client name (also used as full task config key)
     */
    public static String clientName(Workflow workflow, TaskItem taskItem) {
        Objects.requireNonNull(workflow, "workflow is null");
        Objects.requireNonNull(taskItem, "taskItem is null");

        final WorkflowDefinitionId id = WorkflowDefinitionId.of(workflow);
        return clientName(id, taskItem.getName());
    }

    /**
     * Generates full OIDC client name for inline task authentication.
     * <p>
     * This is also the full task-level config key per ADR 2026-07-07.
     * <p>
     * Format: {@code namespace:name:version.task.taskName}
     * <p>
     * Example: {@code acme:orders:1.0.0.task.payment}
     *
     * @param workflowId the workflow definition ID
     * @param taskName the task name
     * @return composite client name (also used as full task config key)
     */
    public static String clientName(WorkflowDefinitionId workflowId, String taskName) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        Objects.requireNonNull(taskName, "taskName is null");

        return workflowId.toString(SEPARATOR) + TASK_SEGMENT + taskName;
    }

    // ========== Progressive Config Keys (User-Facing Routing) ==========

    /**
     * Task-level config key with medium specificity (no version).
     * <p>
     * Format: {@code namespace:name.task.taskName}
     * <p>
     * Example: {@code acme:orders.task.payment}
     *
     * @param workflowId the workflow definition ID
     * @param taskName the task name
     * @return medium task config key
     */
    public static String taskConfigKeyMedium(WorkflowDefinitionId workflowId, String taskName) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        Objects.requireNonNull(taskName, "taskName is null");

        return workflowId.namespace() + SEPARATOR + workflowId.name() + TASK_SEGMENT + taskName;
    }

    /**
     * Task-level config key with short specificity (name only).
     * <p>
     * Format: {@code name.task.taskName}
     * <p>
     * Example: {@code orders.task.payment}
     *
     * @param workflowId the workflow definition ID
     * @param taskName the task name
     * @return short task config key
     */
    public static String taskConfigKeyShort(WorkflowDefinitionId workflowId, String taskName) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        Objects.requireNonNull(taskName, "taskName is null");

        return workflowId.name() + TASK_SEGMENT + taskName;
    }

    /**
     * Workflow-level config key with full specificity.
     * <p>
     * Format: {@code namespace:name:version}
     * <p>
     * Example: {@code acme:orders:1.0.0}
     *
     * @param workflowId the workflow definition ID
     * @return full workflow config key
     */
    public static String workflowConfigKeyFull(WorkflowDefinitionId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        return workflowId.toString(SEPARATOR);
    }

    /**
     * Workflow-level config key with medium specificity (no version).
     * <p>
     * Format: {@code namespace:name}
     * <p>
     * Example: {@code acme:orders}
     *
     * @param workflowId the workflow definition ID
     * @return medium workflow config key
     */
    public static String workflowConfigKeyMedium(WorkflowDefinitionId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        return workflowId.namespace() + SEPARATOR + workflowId.name();
    }

    /**
     * Workflow-level config key with short specificity (name only).
     * <p>
     * Format: {@code name}
     * <p>
     * Example: {@code orders}
     *
     * @param workflowId the workflow definition ID
     * @return short workflow config key
     */
    public static String workflowConfigKeyShort(WorkflowDefinitionId workflowId) {
        Objects.requireNonNull(workflowId, "workflowId is null");
        return workflowId.name();
    }
}
