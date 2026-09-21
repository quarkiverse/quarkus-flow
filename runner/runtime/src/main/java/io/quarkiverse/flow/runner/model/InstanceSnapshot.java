package io.quarkiverse.flow.runner.model;

import java.time.Instant;

import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * An immutable snapshot of an in-memory workflow instance.
 * Captured from the active instance registry at query time.
 *
 * @param input the workflow instance's input, or {@code null} unless the caller opted in via the
 *        {@code includeInput} query parameter (omitted by default to avoid inflating the response
 *        with potentially large payloads)
 */
public record InstanceSnapshot(
        String instanceId,
        String workflowName,
        String workflowNamespace,
        String workflowVersion,
        WorkflowStatus status,
        Instant startedAt,
        Object input) {
}
