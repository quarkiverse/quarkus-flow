package io.quarkiverse.flow.persistence.mvstore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.persistence.mvstore.MVStorePersistenceStore;

@ApplicationScoped
public class MVStoreProducer {

    @Inject
    WorkflowBufferFactory factory;

    @Inject
    MVStoreConfig config;

    @Produces
    @ApplicationScoped
    PersistenceInstanceStore mvStore() {
        return new MVStorePersistenceStore(config.dbPath(), factory);
    }
}
