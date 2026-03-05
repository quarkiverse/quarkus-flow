package io.quarkiverse.flow.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class WorkflowRegistryInitializer {

    @Inject
    WorkflowRegistry registry;

    void onStart(@Observes StartupEvent ev) {
        registry.warmUp();
    }
}
