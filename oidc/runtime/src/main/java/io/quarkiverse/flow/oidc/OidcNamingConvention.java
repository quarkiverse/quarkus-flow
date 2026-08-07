package io.quarkiverse.flow.oidc;

import java.util.Objects;

import io.quarkiverse.flow.config.ClientNamingConvention;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * OIDC-specific naming conventions that delegate to {@link ClientNamingConvention}.
 * <p>
 * Retains the DSL-level overload {@link #clientName(Workflow, TaskItem)} for
 * extracting identity from workflow/task objects.
 */
public final class OidcNamingConvention {

    private OidcNamingConvention() {
    }

    /**
     * Generates full OIDC client name from DSL workflow and task objects.
     *
     * @param workflow the workflow
     * @param taskItem the task item
     * @return composite client name: {@code namespace:name:version.task.taskName}
     */
    public static String clientName(Workflow workflow, TaskItem taskItem) {
        Objects.requireNonNull(workflow, "workflow is null");
        Objects.requireNonNull(taskItem, "taskItem is null");

        final WorkflowDefinitionId id = WorkflowDefinitionId.of(workflow);
        return ClientNamingConvention.taskKeyFull(id, taskItem.getName());
    }

    /** @see ClientNamingConvention#taskKeyFull(WorkflowDefinitionId, String) */
    public static String clientName(WorkflowDefinitionId workflowId, String taskName) {
        return ClientNamingConvention.taskKeyFull(workflowId, taskName);
    }

    /** @see ClientNamingConvention#taskKeyMedium(WorkflowDefinitionId, String) */
    public static String taskConfigKeyMedium(WorkflowDefinitionId workflowId, String taskName) {
        return ClientNamingConvention.taskKeyMedium(workflowId, taskName);
    }

    /** @see ClientNamingConvention#taskKeyShort(WorkflowDefinitionId, String) */
    public static String taskConfigKeyShort(WorkflowDefinitionId workflowId, String taskName) {
        return ClientNamingConvention.taskKeyShort(workflowId, taskName);
    }

    /** @see ClientNamingConvention#workflowKeyFull(WorkflowDefinitionId) */
    public static String workflowConfigKeyFull(WorkflowDefinitionId workflowId) {
        return ClientNamingConvention.workflowKeyFull(workflowId);
    }

    /** @see ClientNamingConvention#workflowKeyMedium(WorkflowDefinitionId) */
    public static String workflowConfigKeyMedium(WorkflowDefinitionId workflowId) {
        return ClientNamingConvention.workflowKeyMedium(workflowId);
    }

    /** @see ClientNamingConvention#workflowKeyShort(WorkflowDefinitionId) */
    public static String workflowConfigKeyShort(WorkflowDefinitionId workflowId) {
        return ClientNamingConvention.workflowKeyShort(workflowId);
    }
}
