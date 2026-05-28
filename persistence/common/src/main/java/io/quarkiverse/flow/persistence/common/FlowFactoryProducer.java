package io.quarkiverse.flow.persistence.common;

import java.util.concurrent.ExecutorService;

import jakarta.enterprise.inject.Produces;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.marshaller.DefaultBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.AsyncPersistenceExecutor;
import io.serverlessworkflow.impl.persistence.PersistenceExecutor;

public class FlowFactoryProducer {
    @Produces
    @DefaultBean
    WorkflowBufferFactory workflowBufferFactory() {
        return DefaultBufferFactory.factory();
    }

    @Produces
    @DefaultBean
    PersistenceExecutor persistenceExecutor(ExecutorService service) {
        return new AsyncPersistenceExecutor(service);
    }
}
