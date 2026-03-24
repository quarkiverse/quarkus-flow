package io.quarkiverse.flow.persistence.jpa;

import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.persistence.PersistenceExecutor;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceOperations;
import io.serverlessworkflow.impl.persistence.SyncPersistenceExecutor;
import io.serverlessworkflow.impl.persistence.TransactedPersistenceInstanceWriter;

@ApplicationScoped
public class JpaInstanceWriter extends TransactedPersistenceInstanceWriter {

    @Inject
    JpaInstanceOperations operations;

    @Override
    protected void doTransaction(Consumer<PersistenceInstanceOperations> operation, WorkflowDefinitionData definition) {
        operation.accept(operations);
    }

    @Override
    protected PersistenceExecutor persistenceExecutor() {
        return new SyncPersistenceExecutor();
    }
}
