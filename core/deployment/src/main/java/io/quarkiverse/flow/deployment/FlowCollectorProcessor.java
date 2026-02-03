package io.quarkiverse.flow.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.jboss.jandex.ClassInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Processor responsible for reading Workflow definitions and producing necessary build items.
 */
public class FlowCollectorProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowCollectorProcessor.class);
    private static final Set<String> SUPPORTED_WORKFLOW_FILE_EXTENSIONS = Set.of(".json", ".yaml", ".yml");

    /**
     * If the configured directory is relative to the module, we must identify where we are.
     * Usually, outputDir is `target/classes`, so then we try to go up until we get the target parent, which is the module root.
     * In synthetic projects (aka QuarkusDevModeTest), the module root is within a temp dir inside `target`
     * (target/quarkus-dev-mode-testXXXXX), in this scenario the module root is the temp dir.
     */
    private static Path findModuleRootFromTarget(Path outputDir) {
        Path targetDir = outputDir;
        while (targetDir != null && !"target".equals(targetDir.getFileName().toString())) {
            targetDir = targetDir.getParent();
        }

        if (targetDir != null && targetDir.getParent() != null) {
            return targetDir.getParent();
        }

        if (outputDir != null && outputDir.getParent() != null) {
            return outputDir.getParent();
        }
        return outputDir;
    }

    private static Set<DiscoveredWorkflowBuildItem> collectUniqueWorkflowFileData(
            Path flowDir) {
        Set<DiscoveredWorkflowBuildItem> items = new HashSet<>();

        try (var stream = Files.walk(flowDir)) {
            stream.filter(file -> Files.isRegularFile(file) && SUPPORTED_WORKFLOW_FILE_EXTENSIONS.stream()
                    .anyMatch(ext -> file.getFileName().toString().endsWith(ext)))
                    .forEach(consumeWorkflowFile(items));
        } catch (IOException e) {
            LOG.error("Failed to scan flow resources in path: {}", flowDir, e);
            throw new UncheckedIOException(
                    "Error while scanning flow resources in path: " + flowDir, e);
        }
        return items;
    }

    private static Consumer<Path> consumeWorkflowFile(Set<DiscoveredWorkflowBuildItem> workflowsSet) {
        return file -> {
            try {
                Workflow workflow = WorkflowReader.readWorkflow(file);
                DiscoveredWorkflowBuildItem buildItem = DiscoveredWorkflowBuildItem.isFromSpec(file, workflow);
                if (!workflowsSet.add(buildItem)) {
                    WorkflowDefinitionId id = buildItem.workflowDefinitionId();
                    throw new IllegalStateException(String.format(
                            "Duplicate workflow detected: namespace='%s', name='%s' and version='%s'",
                            id.namespace(), id.name(), id.version()));
                }
            } catch (IOException e) {
                LOG.error("Failed to parse workflow file at path: {}", file, e);
                throw new UncheckedIOException("Error while parsing workflow file: " + file, e);
            }
        };
    }

    /**
     * Collect all beans that implement the {@link io.quarkiverse.flow.Flowable } interface.
     */
    @BuildStep
    void collectFlows(CombinedIndexBuildItem index, BuildProducer<DiscoveredWorkflowBuildItem> wf) {
        for (ClassInfo flow : index.getIndex().getAllKnownImplementations(DotNames.FLOWABLE)) {
            if (flow.isInterface() || Modifier.isAbstract(flow.flags())) {
                continue;
            }
            wf.produce(DiscoveredWorkflowBuildItem.isFromSource(flow.name().toString()));
        }
    }

    /**
     * Collect all workflow files from the specified flow directory and produce
     * build items for each unique workflow.
     */
    @BuildStep
    public void collectUniqueWorkflowFileData(OutputTargetBuildItem outputTarget,
            FlowDefinitionsConfig flowDefinitionsConfig,
            BuildProducer<DiscoveredWorkflowBuildItem> workflows) {

        final Path configuredPath = Paths.get(flowDefinitionsConfig.dir().orElse(FlowDefinitionsConfig.DEFAULT_FLOW_DIR));
        final Path flowResourcesPath = configuredPath.isAbsolute()
                ? configuredPath
                : findModuleRootFromTarget(outputTarget.getOutputDirectory()).resolve(configuredPath).normalize();

        if (Files.exists(flowResourcesPath)) {
            Set<DiscoveredWorkflowBuildItem> uniqueBuildItems = collectUniqueWorkflowFileData(flowResourcesPath);
            uniqueBuildItems.forEach(workflows::produce);
        }
    }
}
