package io.quarkiverse.flow.producers;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.events.InMemoryEvents;

public class InMemoryEventsBean {

    @Produces
    @Singleton
    @DefaultBean
    InMemoryEvents inMemoryEvents() {
        return new InMemoryEvents();
    }

    @SuppressWarnings("rawtypes")
    @Produces
    @Singleton
    @DefaultBean
    EventConsumer eventConsumer(InMemoryEvents delegate) {
        return delegate;
    }

    @Produces
    @Singleton
    @DefaultBean
    EventPublisher eventPublisher(InMemoryEvents delegate) {
        return delegate;
    }

}
