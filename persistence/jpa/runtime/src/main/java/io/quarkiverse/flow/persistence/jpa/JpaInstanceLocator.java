package io.quarkiverse.flow.persistence.jpa;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.flow.persistence.spi.InstanceLocation;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;

@ApplicationScoped
public class JpaInstanceLocator implements InstanceLocator {

    @Inject
    WorkflowInstanceRepository repository;

    @Override
    @Transactional
    public Optional<InstanceLocation> locate(String namespace, String name, String version, String instanceId) {
        return repository
                .find("key.instanceId = ?1 and workflowNamespace = ?2 and workflowName = ?3 and workflowVersion = ?4",
                        instanceId, namespace, name, version)
                .firstResultOptional()
                .map(e -> new InstanceLocation(
                        e.getWorkflowNamespace(),
                        e.getWorkflowName(),
                        e.getWorkflowVersion(),
                        e.getInstanceId(),
                        e.getApplicationId(),
                        e.getStatus(),
                        e.getStartedAt()));
    }
}
