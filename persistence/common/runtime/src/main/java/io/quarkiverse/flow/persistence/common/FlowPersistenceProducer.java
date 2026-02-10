package io.quarkiverse.flow.persistence.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.persistence.DefaultPersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;

@ApplicationScoped
public class FlowPersistenceProducer {

    @Inject
    PersistenceInstanceStore store;

    @Produces
    @ApplicationScoped
    @Unremovable
    PersistenceInstanceHandlers persistenceHandlers() {
        return DefaultPersistenceInstanceHandlers.from(store);
    }
}
