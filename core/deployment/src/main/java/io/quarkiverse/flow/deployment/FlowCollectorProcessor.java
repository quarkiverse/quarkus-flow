package io.quarkiverse.flow.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
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

/**
 * Processor responsible for reading Workflow definitions and producing necessary build items.
 */
public class FlowCollectorProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowCollectorProcessor.class);
    private static final Set<String> SUPPORTED_WORKFLOW_FILE_EXTENSIONS = Set.of(".json", ".yaml", ".yml");

    /**
     * Collect all beans that implement the {@link io.quarkiverse.flow.Flow } interface.
     */
    @BuildStep
    void collectFlows(CombinedIndexBuildItem index, BuildProducer<DiscoveredFlowBuildItem> wf) {
        for (ClassInfo flow : index.getIndex().getAllKnownSubclasses(DotNames.FLOW)) {
            if (flow.isAbstract())
                continue;
            wf.produce(new DiscoveredFlowBuildItem(flow.name().toString()));
        }
    }

    /**
     * Collect all workflow files from the specified flow directory and produce
     * build items for each unique workflow.
     */
    @BuildStep
    public void collectUniqueWorkflowFileData(OutputTargetBuildItem outputTarget,
            FlowDefinitionsConfig flowDefinitionsConfig,
            BuildProducer<DiscoveredWorkflowFileBuildItem> workflows) throws IOException {

        Path flowDir = Paths.get(flowDefinitionsConfig.dir().orElse(FlowDefinitionsConfig.DEFAULT_FLOW_DIR));
        final Path flowResourcesPath = outputTarget.getOutputDirectory().resolve(
                Paths.get("..", "src", "main").resolve(flowDir));
        if (Files.exists(flowResourcesPath)) {
            Set<DiscoveredWorkflowFileBuildItem> uniqueBuildItems = collectUniqueWorkflowFileData(flowResourcesPath);
            uniqueBuildItems.forEach(workflows::produce);
        }
    }

    private static Set<DiscoveredWorkflowFileBuildItem> collectUniqueWorkflowFileData(
            Path flowDir) {
        Set<DiscoveredWorkflowFileBuildItem> items = new HashSet<>();

        try (var stream = Files.walk(flowDir)) {
            stream.filter(file -> Files.isRegularFile(file) && SUPPORTED_WORKFLOW_FILE_EXTENSIONS.stream()
                    .anyMatch(ext -> file.getFileName().toString().endsWith(ext))).forEach(consumeWorkflowFile(items));
        } catch (IOException e) {
            LOG.error("Failed to scan flow resources in path: {}", flowDir, e);
            throw new UncheckedIOException(
                    "Error while scanning flow resources in path: " + flowDir, e);
        }
        return items;
    }

    private static Consumer<Path> consumeWorkflowFile(Set<DiscoveredWorkflowFileBuildItem> workflowsSet) {
        return file -> {
            try {
                Workflow workflow = WorkflowReader.readWorkflow(file);
                DiscoveredWorkflowFileBuildItem buildItem = new DiscoveredWorkflowFileBuildItem(file,
                        workflow.getDocument().getNamespace(),
                        workflow.getDocument().getName());
                if (!workflowsSet.add(buildItem)) {
                    LOG.warn("Duplicate workflow detected: namespace='{}', name='{}'. The file at '{}' will be ignored.",
                            buildItem.namespace(), buildItem.name(), file.toAbsolutePath());
                }
            } catch (IOException e) {
                LOG.error("Failed to parse workflow file at path: {}", file, e);
                throw new UncheckedIOException("Error while parsing workflow file: " + file, e);
            }
        };
    }
}
