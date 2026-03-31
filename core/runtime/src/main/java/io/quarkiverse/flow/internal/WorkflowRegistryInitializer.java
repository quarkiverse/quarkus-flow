package io.quarkiverse.flow.internal;

import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.StartupEvent;
import io.serverlessworkflow.impl.WorkflowApplication;

@ApplicationScoped
public class WorkflowRegistryInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRegistryInitializer.class);

    @Inject
    WorkflowRegistry registry;

    @Inject
    WorkflowApplication application;

    void onStart(@Observes StartupEvent ev) {
        LOG.debug("Flow: Starting Workflow Registry");
        CompletableFuture.runAsync(() -> {
            try {
                // Force the CDI container to fully initialize the WorkflowApplication.
                application.id();
                // Now it is perfectly safe to warm up the registry.
                LOG.info("Flow: WorkflowApplication is ready. Starting Workflow Registry warmup.");
                registry.warmUp();

            } catch (Exception e) {
                LOG.error("Flow: Failed to initialize and warm up workflows", e);
            }
        });
    }
}
