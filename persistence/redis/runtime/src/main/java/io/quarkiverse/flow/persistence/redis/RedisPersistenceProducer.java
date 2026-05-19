package io.quarkiverse.flow.persistence.redis;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.serverlessworkflow.impl.persistence.DefaultPersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceAllStrategyCorrelationInfoFactories;
import io.serverlessworkflow.impl.persistence.PersistenceExecutor;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.scheduler.AllStrategyCorrelationInfoFactory;

@ApplicationScoped
public class RedisPersistenceProducer {

    @Produces
    @ApplicationScoped
    PersistenceInstanceHandlers redisPersistenceHandlers(PersistenceInstanceStore store, PersistenceExecutor executor) {
        return DefaultPersistenceInstanceHandlers.builder(store).withPersistenceExecutor(executor).build();
    }

    @ApplicationScoped
    @Produces
    AllStrategyCorrelationInfoFactory allStrategy(PersistenceInstanceStore store, PersistenceExecutor executor) {
        return PersistenceAllStrategyCorrelationInfoFactories.from(executor, store);
    }
}
