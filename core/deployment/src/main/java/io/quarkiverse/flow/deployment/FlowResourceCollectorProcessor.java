package io.quarkiverse.flow.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
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
     * Marks any dependency JAR containing the configured flow directory as an application archive,
     * so its workflow files become visible to {@link #collectWorkflowFiles}. This allows plain
     * resources-only JARs (no Jandex index or beans.xml) to contribute workflow definitions.
     */
    @BuildStep
    public void flowDirectoryArchiveMarker(
            FlowDefinitionsConfig flowDefinitionsConfig,
            BuildProducer<AdditionalApplicationArchiveMarkerBuildItem> markers) {
        if (flowDefinitionsConfig.scanDependencies()) {
            markers.produce(new AdditionalApplicationArchiveMarkerBuildItem(
                    flowDefinitionsConfig.dir().orElse(FlowDefinitionsConfig.DEFAULT_FLOW_DIR)));
        }
    }

    /**
     * Collect all workflow files from application archives and produce
     * build items for each unique workflow.
     * <p>
     * Quarkus ApplicationArchivesBuildItem provides access to all application resources,
     * with test resources automatically taking precedence over main resources.
     * When {@code quarkus.flow.definitions.scan-dependencies} is enabled (default),
     * dependency JARs containing the flow directory are scanned as well. This feature targets the
     * application's direct dependencies: no dependency graph resolution is performed to filter
     * out transitive ones. The application's own workflows take precedence over
     * dependency-provided ones with the same identifier.
     */
    @BuildStep
    public void collectWorkflowFiles(
            ApplicationArchivesBuildItem archives,
            FlowDefinitionsConfig flowDefinitionsConfig,
            BuildProducer<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources) {

        final String flowResourcePath = flowDefinitionsConfig.dir().orElse(FlowDefinitionsConfig.DEFAULT_FLOW_DIR);
        final boolean followSymlinks = flowDefinitionsConfig.followSymlinks();
        Map<String, DiscoveredWorkflowBuildItem> workflowsMap = new HashMap<>();
        Map<String, String> workflowOrigins = new HashMap<>();

        // Scan the root archive first (application's own resources)
        // In test mode, the root archive contains merged src/main/resources + src/test/resources
        // with test resources taking precedence
        scanArchive(archives.getRootArchive(), true, flowResourcePath, followSymlinks, workflowsMap, workflowOrigins);

        // Then scan dependency archives; JARs containing the flow directory are application
        // archives thanks to the marker produced by flowDirectoryArchiveMarker
        if (flowDefinitionsConfig.scanDependencies()) {
            for (ApplicationArchive archive : archives.getApplicationArchives()) {
                scanArchive(archive, false, flowResourcePath, followSymlinks, workflowsMap, workflowOrigins);
            }
        }

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

    private static void scanArchive(ApplicationArchive archive, boolean rootArchive, String flowResourcePath,
            boolean followSymlinks, Map<String, DiscoveredWorkflowBuildItem> workflowsMap,
            Map<String, String> workflowOrigins) {
        final String archiveLabel = rootArchive ? "application"
                : archive.getKey() != null ? archive.getKey().toGacString() : "unknown archive";

        // walkIfContains short-circuits archives without the flow directory (e.g. Jandex-indexed
        // dependencies that are application archives for other reasons) and visits only its subtree
        archive.accept(tree -> tree.walkIfContains(flowResourcePath, visit -> {
            String relativePath = visit.getRelativePath("/");

            // Only process files in the configured flow directory
            if (relativePath.startsWith(flowResourcePath + "/") &&
                    WorkflowNameUtils.SUPPORTED_WORKFLOW_FILE_EXTENSIONS.stream()
                            .anyMatch(relativePath::endsWith)
                    &&
                    (!Files.isSymbolicLink(visit.getPath()) || followSymlinks)) {

                Path filePath = visit.getPath();
                try {
                    // Parse workflow to extract metadata (namespace, name, version)
                    Workflow workflow = WorkflowReader.readWorkflow(filePath);

                    // No need to read content - we'll load from classpath at runtime
                    DiscoveredWorkflowBuildItem item = rootArchive
                            ? DiscoveredWorkflowBuildItem.fromSpec(relativePath, workflow)
                            : DiscoveredWorkflowBuildItem.fromDependencySpec(relativePath, workflow);

                    tryAddUniqueWorkflow(item, archiveLabel, workflowsMap, workflowOrigins);
                    LOG.debug("Discovered workflow: {} at {}", item.workflowDefinitionId(), relativePath);
                } catch (IOException e) {
                    LOG.error("Failed to parse workflow file: {}", filePath, e);
                    throw new UncheckedIOException("Error parsing workflow file: " + filePath, e);
                }
            }
        }));
    }

    private static void tryAddUniqueWorkflow(DiscoveredWorkflowBuildItem item, String archiveLabel,
            Map<String, DiscoveredWorkflowBuildItem> uniqueWorkflows, Map<String, String> workflowOrigins) {
        DiscoveredWorkflowBuildItem existing = uniqueWorkflows.get(item.specIdentifier());

        if (existing == null) {
            uniqueWorkflows.put(item.specIdentifier(), item);
            workflowOrigins.put(item.specIdentifier(), archiveLabel);
            return;
        }

        if (item.fromSpec()) {
            // Allow test resources to override main resources (Maven convention)
            // The tree walk processes resources in order, so the last one wins
            uniqueWorkflows.put(item.specIdentifier(), item);
            workflowOrigins.put(item.specIdentifier(), archiveLabel);
            LOG.debug("Workflow {} found in multiple locations - using {}, overriding {}",
                    item.workflowDefinitionId(),
                    item.definitionResourcePath(),
                    existing.definitionResourcePath());
            return;
        }

        if (existing.fromSpec()) {
            // The application's own workflow always wins over a dependency-provided one
            LOG.info("Workflow {}: application definition at '{}' overrides dependency-provided definition at '{}' ({})",
                    item.workflowDefinitionId(),
                    existing.definitionResourcePath(),
                    item.definitionResourcePath(),
                    archiveLabel);
            return;
        }

        // Same workflow identifier contributed by two dependency archives: fail the build,
        // silently picking one would depend on classpath ordering and be non-reproducible
        throw new IllegalStateException(String.format(
                "Duplicate workflow '%s' contributed by multiple dependencies: '%s' (%s) and '%s' (%s). " +
                        "Workflow identifiers (namespace:name:version) must be unique across dependencies. " +
                        "Rename one of them, or disable dependency scanning with " +
                        "quarkus.flow.definitions.scan-dependencies=false.",
                item.specIdentifier(),
                existing.definitionResourcePath(),
                workflowOrigins.get(item.specIdentifier()),
                item.definitionResourcePath(),
                archiveLabel));
    }
}
