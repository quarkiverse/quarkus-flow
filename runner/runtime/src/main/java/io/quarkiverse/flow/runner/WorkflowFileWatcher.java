package io.quarkiverse.flow.runner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.quarkiverse.flow.internal.WorkflowRegistrarService;
import io.quarkus.arc.Unremovable;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@ApplicationScoped
@Unremovable
public class WorkflowFileWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowFileWatcher.class.getName());
    static final String JOB_IDENTITY = "flow-runner-file-watcher";

    @Inject
    WorkflowRegistrarService registrarService;

    @Inject
    WorkflowApplication application;

    @Inject
    WorkflowDefinitionRuntimeLoader loader;

    @Inject
    FlowRunnerConfig config;

    @Inject
    Scheduler scheduler;

    final Map<Path, WorkflowDefinitionId> registeredFiles = new HashMap<>();
    private volatile boolean active = false;

    void onStart(@Observes WorkflowApplicationReadyEvent ev) {
        if (config.source().path().isEmpty()) {
            LOGGER.debug("Flow Runner: File watcher skipped — source path not configured");
            return;
        }

        buildBaseline();

        scheduler.newJob(JOB_IDENTITY)
                .setInterval(config.source().watch().interval())
                .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                .setTask(executionContext -> poll())
                .schedule();

        active = true;
        LOGGER.info("Flow Runner: File watcher started — monitoring {} with interval {} ({} files in baseline)",
                config.source().path().get(), config.source().watch().interval(), registeredFiles.size());
    }

    @PreDestroy
    void stop() {
        if (active) {
            scheduler.unscheduleJob(JOB_IDENTITY);
            LOGGER.debug("Flow Runner: File watcher stopped");
        }
    }

    void buildBaseline() {
        for (Path path : loader.scanWorkflowFiles()) {
            registeredFiles.put(path, null);
        }
    }

    void poll() {
        List<Path> currentFiles = loader.scanWorkflowFiles();

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
