package io.quarkiverse.flow.runner.model;

import java.time.Instant;

import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * An immutable snapshot of an in-memory workflow instance.
 * Captured from the active instance registry at query time.
 */
public record InstanceSnapshot(
        String instanceId,
        String workflowName,
        String workflowNamespace,
        String workflowVersion,
        WorkflowStatus status,
        Instant startedAt) {

    public static InstanceSnapshot from(WorkflowDefinitionId id, WorkflowInstance instance) {
        return new InstanceSnapshot(
                instance.id(),
                id.name(),
                id.namespace(),
                id.version(),
                instance.status(),
                instance.startedAt());
    }
}
