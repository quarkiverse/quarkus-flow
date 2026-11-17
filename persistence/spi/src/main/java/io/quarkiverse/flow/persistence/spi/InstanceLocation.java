package io.quarkiverse.flow.persistence.spi;

/**
 * Where a workflow instance lives: the owning applicationId (the lease shard), resolved by InstanceLocator. Carries no
 * workflow payload.
 */
public record InstanceLocation(
        String namespace,
        String name,
        String version,
        String instanceId,
        String applicationId) {
}
