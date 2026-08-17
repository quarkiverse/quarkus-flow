package io.quarkiverse.flow.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
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

import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistrarService;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@ApplicationScoped
@Unremovable
public class WorkflowDefinitionRuntimeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowDefinitionRuntimeLoader.class.getName());

    @Inject
    WorkflowRegistrarService registrarService;

    @Inject
    FlowRunnerConfig config;

    void onStart(@Observes WorkflowApplicationReadyEvent ev) {
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
        Path basePath = Path.of(config.source().path().orElse(""));
        LOGGER.info("Flow Runner: Loading workflows from filesystem: {}", basePath);

        if (!Files.exists(basePath))
            throw new IllegalStateException("Workflow directory does not exist: " + basePath);

        if (!Files.isDirectory(basePath))
            throw new IllegalStateException("Workflow path is not a directory: " + basePath);

        if (Files.isSymbolicLink(basePath) && !config.source().followSymlinks()) {
            LOGGER.debug(
                    "Flow Runner: path is a symbolic link: {}, skipping it. To enable processing symlinks set quarkus.flow.runner.source.follow-symlinks=true.",
                    basePath);
            return new ArrayList<>();
        }

        final List<WorkflowDefinitionId> workflowDefinitionIds = new ArrayList<>();
        for (Path path : scanWorkflowFiles()) {
            final WorkflowDefinitionId defId = loadWorkflow(path);
            workflowDefinitionIds.stream().filter(addedId -> addedId.equals(defId)).findFirst().ifPresent(duplicatedId -> {
                throw new IllegalStateException(
                        String.format("Duplicated workflow definition (%s) found in path %s", defId.toString(":"), path));
            });
            workflowDefinitionIds.add(defId);
        }
        return workflowDefinitionIds;
    }

    /**
     * Scans the configured source path and returns all supported workflow file paths.
     * Respects the {@code followSymlinks} configuration.
     *
     * @return list of workflow file paths, or empty list if the path is not configured or invalid
     */
    List<Path> scanWorkflowFiles() {
        if (config.source().path().isEmpty()) {
            return List.of();
        }
        Path basePath = Path.of(config.source().path().get());
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            return List.of();
        }
        try (Stream<Path> paths = config.source().followSymlinks()
                ? Files.walk(basePath, FileVisitOption.FOLLOW_LINKS).sorted()
                : Files.walk(basePath).sorted()) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedWorkflowFile)
                    .filter(f -> !Files.isSymbolicLink(f) || config.source().followSymlinks())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Flow Runner: Failed to scan workflow directory: " + basePath, e);
        }
    }

    private boolean isSupportedWorkflowFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return WorkflowNameUtils.SUPPORTED_WORKFLOW_FILE_EXTENSIONS.stream()
                .anyMatch(fileName::endsWith);
    }

    private WorkflowDefinitionId loadWorkflow(Path path) {
        try {
            Workflow workflow = WorkflowReader.readWorkflow(path);

            if (workflow.getDocument().getNamespace() == null || workflow.getDocument().getName() == null
                    || workflow.getDocument().getVersion() == null) {
                throw new IllegalStateException(
                        String.format("Flow Runner: Workflow at %s is missing required fields (namespace, name, or version)",
                                path));
            }

            registrarService.register(workflow);

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
