package io.quarkiverse.flow.persistence.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

public class JpaPersistenceHandlerProducer {

    @ApplicationScoped
    @Produces
    PersistenceInstanceHandlers jpaPersistenceHandlers(JpaInstanceWriter writer, JpaInstanceReader reader) {
        return new PersistenceInstanceHandlers(writer, reader);
    }
}
