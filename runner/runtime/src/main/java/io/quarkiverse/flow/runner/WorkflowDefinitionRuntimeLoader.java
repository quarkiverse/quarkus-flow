package io.quarkiverse.flow.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowApplicationReady;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@ApplicationScoped
@Unremovable
public class WorkflowDefinitionRuntimeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowDefinitionRuntimeLoader.class.getName());

    @Inject
    WorkflowApplication application;

    @Inject
    FlowRunnerConfig config;

    void onStart(@Observes WorkflowApplicationReady ev) {
        if (!config.enabled()) {
            LOGGER.info("Flow Runner: Workflow runner is disabled. Skipping loading Workflow Definitions from file system.");
            return;
        }
        if (config.source().path().isEmpty()) {
            LOGGER.info(
                    "Flow Runner: quarkus.flow.runner.source.path is not defined, skipping loading Workflow Definitions from file system.");
            return;
        }
        final List<WorkflowDefinitionId> workflowDefinitionIds = loadWorkflowDefinitions();
        if (workflowDefinitionIds.isEmpty())
            LOGGER.warn(
                    "Flow Runner: No Workflow Definitions found in path {}, make sure you have valid workflow definition files with extensions: {}",
                    config.source().path(),
                    WorkflowNameUtils.SUPPORTED_WORKFLOW_FILE_EXTENSIONS);
        else
            LOGGER.info("Flow Runner: Workflow Definitions loaded for {}:\n{}", config.source().path(), workflowDefinitionIds);
    }

    private List<WorkflowDefinitionId> loadWorkflowDefinitions() {
        Path basePath = Path.of(config.source().path().get());
        LOGGER.info("Flow Runner: Loading workflows from filesystem: {}", basePath);

        if (!Files.exists(basePath))
            throw new IllegalStateException("Workflow directory does not exist: " + basePath);

        if (!Files.isDirectory(basePath))
            throw new IllegalStateException("Workflow path is not a directory: " + basePath);

        final List<WorkflowDefinitionId> workflowDefinitionIds = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(basePath)) {
            var workflows = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedWorkflowFile)
                    .toList();

            for (Path path : workflows) {
                workflowDefinitionIds.add(loadWorkflow(path));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Flow Runner: Failed to scan workflow directory: " + basePath, e);
        }
        return workflowDefinitionIds;
    }

    private boolean isSupportedWorkflowFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return WorkflowNameUtils.SUPPORTED_WORKFLOW_FILE_EXTENSIONS.stream()
                .anyMatch(fileName::endsWith);
    }

    private WorkflowDefinitionId loadWorkflow(Path path) {
        try {
            // WorkflowReader auto-detects format from file extension
            Workflow workflow = WorkflowReader.readWorkflow(path);

            // Validate required fields
            if (workflow.getDocument().getNamespace() == null || workflow.getDocument().getName() == null
                    || workflow.getDocument().getVersion() == null) {
                throw new IllegalStateException(
                        String.format("Flow Runner: Workflow at %s is missing required fields (namespace, name, or version)", path));
            }

            // Register with WorkflowApplication - creates WorkflowDefinition
            application.workflowDefinition(workflow);

            LOGGER.debug("Flow Runner: Registered workflow {}:{}:{} from {}",
                    workflow.getDocument().getNamespace(),
                    workflow.getDocument().getName(),
                    workflow.getDocument().getVersion(),
                    path);

            return WorkflowDefinitionId.of(workflow);
        } catch (IOException e) {
            throw new UncheckedIOException("Flow Runner: Failed to load workflow from " + path, e);
        }
    }

}
