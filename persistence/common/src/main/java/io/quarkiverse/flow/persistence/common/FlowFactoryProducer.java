package io.quarkiverse.flow.persistence.common;

import jakarta.enterprise.inject.Produces;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.marshaller.DefaultBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;

public class FlowFactoryProducer {
    @Produces
    @DefaultBean
    WorkflowBufferFactory workflowBufferFactory() {
        return DefaultBufferFactory.factory();
    }
}
