package io.quarkiverse.flow.persistence.spi;

import java.time.Instant;

import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * Where a workflow instance lives: the owning applicationId (the lease shard) plus lightweight metainfo, resolved by
 * InstanceLocator. Carries no workflow payload.
 */
public record InstanceLocation(
        String namespace,
        String name,
        String version,
        String instanceId,
        String applicationId,
        WorkflowStatus status,
        Instant startedAt) {
}
