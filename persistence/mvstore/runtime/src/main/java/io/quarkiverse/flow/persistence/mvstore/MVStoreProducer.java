package io.quarkiverse.flow.persistence.mvstore;

import java.util.concurrent.ExecutorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.DefaultPersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.mvstore.MVStorePersistenceStore;

@ApplicationScoped
public class MVStoreProducer {

    @Produces
    @ApplicationScoped
    PersistenceInstanceHandlers mvStoreHandlers(MVStoreConfig config, WorkflowBufferFactory factory, ExecutorService service) {
        return DefaultPersistenceInstanceHandlers.builder(new MVStorePersistenceStore(config.dbPath(), factory))
                .withExecutorService(service).build();
    }
}
