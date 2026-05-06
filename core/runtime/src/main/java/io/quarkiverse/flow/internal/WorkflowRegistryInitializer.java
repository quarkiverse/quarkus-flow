package io.quarkiverse.flow.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.LaunchMode;
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

    @Inject
    LaunchMode launchMode;

    @Inject
    ManagedExecutor executor;

    private volatile WorkflowApplicationInfo appInfo = new WorkflowApplicationInfo();

    void onStart(@Observes StartupEvent ev) {
        LOG.debug("Flow: Starting Workflow Registry");
        if (launchMode == LaunchMode.DEVELOPMENT) {
            LOG.debug("Flow: {} mode detected. Warmup configured as SYNC.", launchMode);
            doStart();
        } else {
            LOG.debug("Flow: {} mode detected. Warmup configured as ASYNC.", launchMode);
            CompletableFuture.runAsync(this::doStart, executor);
        }
    }

    private void doStart() {
        try {
            // Force the CDI container to fully initialize the WorkflowApplication.
            application.id();
            LOG.info("Flow: WorkflowApplication is ready. Starting Workflow Registry warmup.");
            registry.warmUp();
            appInfo = new WorkflowApplicationInfo(application.id());
            LOG.info("Flow: Workflow Registry warmup complete.");
            applicationReadyEvent.fire(new WorkflowApplicationReady(application.id()));
        } catch (Exception e) {
            LOG.error("Flow: Failed to initialize and warm up workflows", e);
            appInfo = new WorkflowApplicationInfo(e);
        }
    }

    public WorkflowApplicationInfo getAppInfo() {
        return appInfo;
    }
}
