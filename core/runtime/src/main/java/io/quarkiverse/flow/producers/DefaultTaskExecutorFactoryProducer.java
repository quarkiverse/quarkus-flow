package io.quarkiverse.flow.producers;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.executors.DefaultTaskExecutorFactory;
import io.serverlessworkflow.impl.executors.TaskExecutorFactory;

public class DefaultTaskExecutorFactoryProducer {

    @Produces
    @Singleton
    @DefaultBean
    TaskExecutorFactory taskExecutorFactory() {
        return DefaultTaskExecutorFactory.get();
    }

}
