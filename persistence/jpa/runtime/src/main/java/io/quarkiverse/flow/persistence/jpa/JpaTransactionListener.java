package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

import io.serverlessworkflow.impl.persistence.hashing.HashMappingCoordinator;

@ApplicationScoped
public class JpaTransactionListener {

    public void afterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) HashMappingCoordinator hashCoordinator) {
        hashCoordinator.afterCommit();
    }

    public void beforeCommit(@Observes(during = TransactionPhase.BEFORE_COMPLETION) HashMappingCoordinator hashCoordinator) {
        hashCoordinator.persist();
    }

    public void afterRollback(@Observes(during = TransactionPhase.AFTER_FAILURE) HashMappingCoordinator hashCoordinator) {
        hashCoordinator.afterRollback();
    }

}
