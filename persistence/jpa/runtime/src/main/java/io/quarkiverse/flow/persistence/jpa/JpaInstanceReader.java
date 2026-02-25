package io.quarkiverse.flow.persistence.jpa;

import java.util.Optional;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.persistence.AbstractPersistenceInstanceReader;

@ApplicationScoped
public class JpaInstanceReader extends AbstractPersistenceInstanceReader {

    @Inject
    JpaInstanceOperations operations;

    @Override
    public Stream<WorkflowInstance> scanAll(WorkflowDefinition definition, String applicationId) {
        return scanAll(operations, definition, applicationId);
    }

    @Override
    public Optional<WorkflowInstance> find(WorkflowDefinition definition, String instanceId) {
        return find(operations, definition, instanceId);
    }
}
