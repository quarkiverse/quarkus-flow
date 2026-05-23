package io.quarkiverse.flow.internal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;

@ApplicationScoped
public class WorkflowApplicationInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowApplicationInitializer.class);

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
        LOG.debug("Flow: Starting WorkflowApplication initialization");
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
            LOG.info("Flow: WorkflowApplication is ready. Starting workflow definitions warmup.");
            warmUpWorkflowDefinitions();
            appInfo = new WorkflowApplicationInfo(application.id());
            LOG.info("Flow: Workflow definitions warmup complete.");
            applicationReadyEvent.fire(new WorkflowApplicationReady(application.id()));
        } catch (Exception e) {
            LOG.error("Flow: Failed to initialize and warm up workflows", e);
            appInfo = new WorkflowApplicationInfo(e);
        }
    }

    private void warmUpWorkflowDefinitions() {
        List<InstanceHandle<WorkflowDefinition>> definitionHandles = Arc.container().listAll(WorkflowDefinition.class);
        LOG.info("Warming up {} WorkflowDefinition beans", definitionHandles.size());
        for (InstanceHandle<WorkflowDefinition> handle : definitionHandles) {
            try {
                // This triggers the synthetic bean's supplier (WorkflowDefinitionRecorder)
                handle.get().workflow();
            } catch (Exception e) {
                LOG.warn("Flow: Failed to warm up WorkflowDefinition from {}",
                        handle.getBean().getIdentifier(), e);
            }
        }
    }

    public WorkflowApplicationInfo getAppInfo() {
        return appInfo;
    }
}
