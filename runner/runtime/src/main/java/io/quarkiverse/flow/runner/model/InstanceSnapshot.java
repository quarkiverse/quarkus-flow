package io.quarkiverse.flow.runner.model;

import java.time.Instant;

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
}
