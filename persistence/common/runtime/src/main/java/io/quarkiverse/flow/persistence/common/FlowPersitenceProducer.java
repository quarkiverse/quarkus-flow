package io.quarkiverse.flow.persistence.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.marshaller.DefaultBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.DefaultPersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;

@ApplicationScoped
public class FlowPersitenceProducer {

    @Inject
    PersistenceInstanceStore store;

    @Produces
    @DefaultBean
    WorkflowBufferFactory workflowBufferFactory() {
        return DefaultBufferFactory.factory();
    }

    @Produces
    @Unremovable
    PersistenceInstanceHandlers persistenceHandlers() {
        return DefaultPersistenceInstanceHandlers.from(store);
    }
}
