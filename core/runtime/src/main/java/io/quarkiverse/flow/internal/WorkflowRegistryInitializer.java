package io.quarkiverse.flow.internal;

import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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

    @Inject
    Event<WorkflowApplicationReady> applicationReadyEvent;

    private volatile WorkflowApplicationInfo appInfo = new WorkflowApplicationInfo();

    void onStart(@Observes StartupEvent ev) {
        LOG.debug("Flow: Starting Workflow Registry");
        CompletableFuture.runAsync(() -> {
            try {
                // Force the CDI container to fully initialize the WorkflowApplication.
                application.id();
                LOG.info("Flow: WorkflowApplication is ready. Starting Workflow Registry warmup.");
                registry.warmUp();
                appInfo = new WorkflowApplicationInfo(application.id());
                applicationReadyEvent.fire(new WorkflowApplicationReady(application.id()));
            } catch (Exception e) {
                LOG.error("Flow: Failed to initialize and warm up workflows", e);
                appInfo = new WorkflowApplicationInfo(e);
            }
        });
    }

    public WorkflowApplicationInfo getAppInfo() {
        return appInfo;
    }
}
