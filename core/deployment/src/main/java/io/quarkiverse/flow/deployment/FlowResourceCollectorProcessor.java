package io.quarkiverse.flow.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Processor responsible for discovering workflow definitions from classpath resource files (YAML/JSON).
 */
public class FlowResourceCollectorProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowResourceCollectorProcessor.class);

    /**
     * Collect all workflow files from application archives and produce
     * build items for each unique workflow.
     * <p>
     * Quarkus ApplicationArchivesBuildItem provides access to all application resources,
     * with test resources automatically taking precedence over main resources.
     */
    @BuildStep
    public void collectWorkflowFiles(
            ApplicationArchivesBuildItem archives,
            FlowDefinitionsConfig flowDefinitionsConfig,
            BuildProducer<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources) {

        final String flowResourcePath = flowDefinitionsConfig.dir().orElse(FlowDefinitionsConfig.DEFAULT_FLOW_DIR);
        Map<String, DiscoveredWorkflowBuildItem> workflowsMap = new HashMap<>();

        // Scan only the root archive (application's own resources)
        // In test mode, the root archive contains merged src/main/resources + src/test/resources
        // with test resources taking precedence
        archives.getRootArchive().accept(tree -> tree.walk(visit -> {
            String relativePath = visit.getRelativePath("/");

            // Only process files in the configured flow directory
            if (relativePath.startsWith(flowResourcePath + "/") &&
                    WorkflowNameUtils.SUPPORTED_WORKFLOW_FILE_EXTENSIONS.stream()
                            .anyMatch(relativePath::endsWith)) {

                Path filePath = visit.getPath();
                try {
                    // Parse workflow to extract metadata (namespace, name, version)
                    Workflow workflow = WorkflowReader.readWorkflow(filePath);

                    // No need to read content - we'll load from classpath at runtime
                    DiscoveredWorkflowBuildItem item = DiscoveredWorkflowBuildItem.fromSpec(
                            relativePath,
                            workflow);

                    tryAddUniqueWorkflow(item, workflowsMap);
                    LOG.debug("Discovered workflow: {} at {}", item.workflowDefinitionId(), relativePath);
                } catch (IOException e) {
                    LOG.error("Failed to parse workflow file: {}", filePath, e);
                    throw new UncheckedIOException("Error parsing workflow file: " + filePath, e);
                }
            }
        }));

        // Produce workflow build items
        workflowsMap.values().forEach(workflows::produce);

        // Register workflow resources for native image compilation
        List<String> resourcePaths = workflowsMap.values().stream()
                .map(DiscoveredWorkflowBuildItem::definitionResourcePath)
                .toList();

        if (!resourcePaths.isEmpty()) {
            nativeImageResources.produce(new NativeImageResourceBuildItem(resourcePaths));
            LOG.info("Registered {} workflow resources for native image compilation", resourcePaths.size());
        }
    }

    private static void tryAddUniqueWorkflow(DiscoveredWorkflowBuildItem item,
            Map<String, DiscoveredWorkflowBuildItem> uniqueWorkflows) {
        DiscoveredWorkflowBuildItem existing = uniqueWorkflows.put(item.specIdentifier(), item);
        if (existing != null) {
            // Allow test resources to override main resources (Maven convention)
            // The tree walk processes resources in order, so the last one wins
            LOG.debug("Workflow {} found in multiple locations - using {}, overriding {}",
                    item.workflowDefinitionId(),
                    item.definitionResourcePath(),
                    existing.definitionResourcePath());
        }
    }
}
