package io.quarkiverse.flow.persistence.jpa;

import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.persistence.PersistenceExecutor;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceOperations;
import io.serverlessworkflow.impl.persistence.TransactedPersistenceInstanceWriter;

@ApplicationScoped
public class JpaInstanceWriter extends TransactedPersistenceInstanceWriter {

    @Inject
    JpaInstanceOperations operations;

    @Inject
    PersistenceExecutor executor;

    @Override
    @Transactional(TxType.REQUIRES_NEW)
    protected void doTransaction(Consumer<PersistenceInstanceOperations> operation, WorkflowDefinitionData definition) {
        operation.accept(operations);
    }

    @Override
    protected PersistenceExecutor persistenceExecutor() {
        return executor;
    }
}
