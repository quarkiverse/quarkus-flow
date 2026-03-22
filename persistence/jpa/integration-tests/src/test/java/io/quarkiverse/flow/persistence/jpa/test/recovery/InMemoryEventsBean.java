package io.quarkiverse.flow.persistence.jpa.test.recovery;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.events.InMemoryEvents;

@DefaultBean
@ApplicationScoped
public class InMemoryEventsBean extends InMemoryEvents {
}
