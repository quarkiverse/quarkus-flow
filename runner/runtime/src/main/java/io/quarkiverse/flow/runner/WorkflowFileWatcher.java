package io.quarkiverse.flow.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.quarkiverse.flow.internal.WorkflowRegistrarService;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class WorkflowFileWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowFileWatcher.class.getName());
    @Inject
    WorkflowRegistrarService registrarService;

    @Inject
    WorkflowApplication application;

    @Inject
    WorkflowDefinitionRuntimeLoader loader;

    @Inject
    FlowRunnerConfig config;

    final Map<Path, WorkflowDefinitionId> registeredFiles = new HashMap<>();
    private volatile boolean active = false;
    private ScheduledExecutorService watchExecutor;

    void onStart(@Observes WorkflowApplicationReadyEvent ev) {
        if (!config.enabled()) {
            LOGGER.debug("Flow Runner: File watcher skipped — runner is disabled");
            return;
        }
        if (config.source().path().isEmpty()) {
            LOGGER.debug("Flow Runner: File watcher skipped — source path not configured");
            return;
        }

        buildBaseline();
        long intervalMillis = config.source().watch().interval().toMillis();
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "quarkus.flow.runner.source.watch.interval must be greater than zero");
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "flow-runner-file-watcher");
            thread.setDaemon(true);
            return thread;
        });
        try {
            executor.scheduleWithFixedDelay(this::pollSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
            watchExecutor = executor;
            active = true;
        } catch (RuntimeException e) {
            executor.shutdownNow();
            throw e;
        }
        LOGGER.info("Flow Runner: File watcher started — monitoring {} with interval {} ({} files in baseline)",
                config.source().path().get(), config.source().watch().interval(), registeredFiles.size());
    }

    @PreDestroy
    void stop() {
        ScheduledExecutorService executor = watchExecutor;
        watchExecutor = null;
        active = false;
        if (executor != null) {
            executor.shutdownNow();
            LOGGER.debug("Flow Runner: File watcher stopped");
        }
    }

    private void pollSafely() {
        try {
            poll();
        } catch (RuntimeException e) {
            LOGGER.warn("Flow Runner: File watcher — polling failed: {}", e.getMessage(), e);
        }
    }

    boolean isWatching() {
        return active && watchExecutor != null && !watchExecutor.isShutdown();
    }

    void buildBaseline() {
        for (Path path : loader.scanWorkflowFiles()) {
            registeredFiles.put(path, null);
        }
    }

    void poll() {
        List<Path> currentFiles;
        try {
            currentFiles = loader.scanWorkflowFiles();
        } catch (UncheckedIOException e) {
            LOGGER.warn("Flow Runner: File watcher — failed to scan directory: {}", e.getMessage());
            return;
        }

        for (Path path : currentFiles) {
            if (!registeredFiles.containsKey(path)) {
                tryRegisterNewFile(path);
            }
        }
    }

    private void tryRegisterNewFile(Path path) {
        try {
            Workflow workflow = WorkflowReader.readWorkflow(path);

            if (workflow.getDocument().getNamespace() == null || workflow.getDocument().getName() == null
                    || workflow.getDocument().getVersion() == null) {
                LOGGER.warn("Flow Runner: File watcher — skipping {} — missing required fields (namespace, name, or version)",
                        path);
                return;
            }

            WorkflowDefinitionId defId = WorkflowDefinitionId.of(workflow);

            if (application.workflowDefinitions().containsKey(defId)) {
                LOGGER.warn("Flow Runner: File watcher — skipping {} — workflow {} is already registered",
                        path, defId.toString(":"));
                registeredFiles.put(path, defId);
                return;
            }

            registrarService.register(workflow);
            registeredFiles.put(path, defId);

            LOGGER.info("Flow Runner: File watcher — registered new workflow {} from {}",
                    defId.toString(":"), path);
        } catch (IOException e) {
            LOGGER.warn("Flow Runner: File watcher — failed to parse {} — will retry next cycle: {}",
                    path, e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("Flow Runner: File watcher — unexpected error processing {}: {}",
                    path, e.getMessage());
        }
    }
}
