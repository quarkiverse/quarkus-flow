package io.quarkiverse.flow.persistence.spi;

import java.util.Optional;

/**
 * Resolves one workflow instance across all shards, scoped to the given workflow id (namespace + name + version).
 * Returns the instance's owning applicationId; returns Optional.empty() when not found.
 * Implemented by durable persistence backends (jpa, redis) as an @ApplicationScoped CDI bean;
 * it is simply absent on runner images built without a persistence backend.
 */
public interface InstanceLocator {

    Optional<InstanceLocation> locate(String namespace, String name, String version, String instanceId);

}
