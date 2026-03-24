package io.quarkiverse.flow.persistence.redis;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.serverlessworkflow.impl.persistence.DefaultPersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;

@ApplicationScoped
public class RedisPersistenceProducer {

    @Produces
    @ApplicationScoped
    PersistenceInstanceHandlers redisPersistenceHandlers(PersistenceInstanceStore store) {
        return DefaultPersistenceInstanceHandlers.builder(store).build();
    }
}
