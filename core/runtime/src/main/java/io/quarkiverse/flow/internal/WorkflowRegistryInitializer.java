package io.quarkiverse.flow.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class WorkflowRegistryInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRegistryInitializer.class);
    @Inject
    WorkflowRegistry registry;

    void onStart(@Observes StartupEvent ev) {
        LOG.debug("Starting Workflow Registry");
        registry.warmUp();
    }
}
