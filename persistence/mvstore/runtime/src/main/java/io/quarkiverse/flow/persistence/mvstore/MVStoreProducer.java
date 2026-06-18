package io.quarkiverse.flow.persistence.mvstore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.DefaultPersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceExecutor;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.mvstore.MVStorePersistenceStore;

@ApplicationScoped
public class MVStoreProducer {

    static final Logger LOG = LoggerFactory.getLogger(MVStoreProducer.class);

    @Produces
    @ApplicationScoped
    MVStorePersistenceStore persistenceStore(MVStoreConfig config, WorkflowBufferFactory factory) {
        return new MVStorePersistenceStore(config.dbPath(), factory);
    }

    @Produces
    @ApplicationScoped
    PersistenceInstanceHandlers mvStoreHandlers(MVStorePersistenceStore store,
            PersistenceExecutor executor) {
        return DefaultPersistenceInstanceHandlers.builder(store)
                .withPersistenceExecutor(executor).build();
    }

    public void close(@Disposes MVStorePersistenceStore persistenceStore) {
        try {
            persistenceStore.close();
        } catch (Exception e) {
            LOG.warn("Error while closing MVStorePersistenceStore", e);
        }
    }
}
