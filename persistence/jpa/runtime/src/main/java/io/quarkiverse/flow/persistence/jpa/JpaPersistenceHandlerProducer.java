package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.serverlessworkflow.impl.persistence.PersistenceAllStrategyCorrelationInfoFactories;
import io.serverlessworkflow.impl.persistence.PersistenceExecutor;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceOperations;
import io.serverlessworkflow.impl.scheduler.AllStrategyCorrelationInfoFactory;

public class JpaPersistenceHandlerProducer {

    @ApplicationScoped
    @Produces
    PersistenceInstanceHandlers jpaPersistenceHandlers(JpaInstanceWriter writer, JpaInstanceReader reader) {
        return new PersistenceInstanceHandlers(writer, reader);
    }

    @ApplicationScoped
    @Produces
    AllStrategyCorrelationInfoFactory correlationFactory(PersistenceExecutor executor,
            PersistenceInstanceOperations operations) {
        return PersistenceAllStrategyCorrelationInfoFactories.from(executor, operations);
    }
}
