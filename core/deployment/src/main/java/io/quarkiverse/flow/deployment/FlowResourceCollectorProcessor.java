package io.quarkiverse.flow.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Processor responsible for discovering workflow definitions from classpath resource files (YAML/JSON).
 */
public class FlowResourceCollectorProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FlowResourceCollectorProcessor.class);

    /**
     * Classpath directory holding the build-generated copies of dependency-provided workflow definitions.
     */
    static final String DEPENDENCY_WORKFLOWS_DIR = "META-INF/quarkus-flow/dependencies";

    private static final Pattern UNSAFE_PATH_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

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
     * direct dependencies containing the flow directory are scanned as well; transitive ones are
     * skipped. The application's own workflows take precedence over dependency-provided ones with the
     * same identifier. Dependency-provided definitions are copied to a unique generated resource, so
     * files sharing the same path in different JARs cannot shadow each other on the classpath.
     */
    @BuildStep
    public void collectWorkflowFiles(
            ApplicationArchivesBuildItem archives,
            FlowDefinitionsConfig flowDefinitionsConfig,
            BuildProducer<DiscoveredWorkflowBuildItem> workflows,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources) {

        final String flowResourcePath = flowDefinitionsConfig.dir().orElse(FlowDefinitionsConfig.DEFAULT_FLOW_DIR);
        final boolean followSymlinks = flowDefinitionsConfig.followSymlinks();
        Map<String, DiscoveredWorkflowBuildItem> workflowsMap = new HashMap<>();
        Map<String, String> workflowOrigins = new HashMap<>();
        Map<String, byte[]> dependencyWorkflowContents = new HashMap<>();

        // Scan the root archive first (application's own resources)
        // In test mode, the root archive contains merged src/main/resources + src/test/resources
        // with test resources taking precedence
        scanArchive(archives.getRootArchive(), null, flowResourcePath, followSymlinks, workflowsMap, workflowOrigins,
                dependencyWorkflowContents);

        // Then scan direct dependency archives; JARs containing the flow directory are application
        // archives thanks to the marker produced by flowDirectoryArchiveMarker
        if (flowDefinitionsConfig.scanDependencies()) {
            int archiveIndex = 0;
            for (ApplicationArchive archive : archives.getArchives()) {
                archiveIndex++;
                if (!isDirectDependency(archive)) {
                    LOG.debug("Skipping workflow discovery in transitive dependency {}", archive.getKey());
                    continue;
                }
                scanArchive(archive, dependencyArchiveId(archive, archiveIndex), flowResourcePath, followSymlinks,
                        workflowsMap, workflowOrigins, dependencyWorkflowContents);
            }
        }

        // Produce workflow build items, and the unique runtime copies of the selected dependency-provided ones
        for (DiscoveredWorkflowBuildItem item : workflowsMap.values()) {
            if (item.fromDependencySpec()) {
                generatedResources.produce(new GeneratedResourceBuildItem(item.definitionResourcePath(),
                        dependencyWorkflowContents.get(item.definitionResourcePath())));
            }
            workflows.produce(item);
        }

        // Register workflow resources for native image compilation
        List<String> resourcePaths = workflowsMap.values().stream()
                .map(DiscoveredWorkflowBuildItem::definitionResourcePath)
                .toList();

        if (!resourcePaths.isEmpty()) {
            nativeImageResources.produce(new NativeImageResourceBuildItem(resourcePaths));
            LOG.info("Registered {} workflow resources for native image compilation", resourcePaths.size());
        }
    }

    /**
     * Only direct dependencies of the application may contribute workflow definitions. Archives without
     * dependency metadata (e.g. explicitly added to the build) are considered direct.
     */
    static boolean isDirectDependency(ApplicationArchive archive) {
        ResolvedDependency dependency = archive.getResolvedDependency();
        return dependency == null || dependency.isDirect();
    }

    /**
     * Builds a path-safe identifier of a dependency archive, used to generate a unique resource path
     * for each of its workflow definitions.
     */
    static String dependencyArchiveId(ApplicationArchive archive, int archiveIndex) {
        ArtifactKey key = archive.getKey();
        if (key == null) {
            return "archive-" + archiveIndex;
        }
        String id = sanitize(key.getGroupId()) + "/" + sanitize(key.getArtifactId());
        if (key.getClassifier() != null && !key.getClassifier().isEmpty()) {
            id += "/" + sanitize(key.getClassifier());
        }
        return id;
    }

    private static String sanitize(String segment) {
        return UNSAFE_PATH_CHARS.matcher(segment).replaceAll("_");
    }

    /**
     * @param dependencyArchiveId unique identifier of the dependency archive, or {@code null} for the root archive
     */
    private static void scanArchive(ApplicationArchive archive, String dependencyArchiveId, String flowResourcePath,
            boolean followSymlinks, Map<String, DiscoveredWorkflowBuildItem> workflowsMap,
            Map<String, String> workflowOrigins, Map<String, byte[]> dependencyWorkflowContents) {
        final boolean rootArchive = dependencyArchiveId == null;
        final String archiveLabel = rootArchive ? "application"
                : archive.getKey() != null ? archive.getKey().toGacString() : dependencyArchiveId;

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

                    DiscoveredWorkflowBuildItem item;
                    if (rootArchive) {
                        // No need to read content - we'll load from classpath at runtime
                        item = DiscoveredWorkflowBuildItem.fromSpec(relativePath, workflow);
                    } else {
                        // Keep the content: the definition is loaded at runtime from a unique generated copy
                        String generatedPath = DEPENDENCY_WORKFLOWS_DIR + "/" + dependencyArchiveId + "/" + relativePath;
                        item = DiscoveredWorkflowBuildItem.fromDependencySpec(relativePath, generatedPath, workflow);
                    }

                    boolean selected = tryAddUniqueWorkflow(item, archiveLabel, workflowsMap, workflowOrigins);
                    if (selected && item.fromDependencySpec()) {
                        // Only read the content of the definitions that will actually be used
                        dependencyWorkflowContents.put(item.definitionResourcePath(), Files.readAllBytes(filePath));
                    }
                    LOG.debug("Discovered workflow: {} at {}", item.workflowDefinitionId(), relativePath);
                } catch (IOException e) {
                    LOG.error("Failed to parse workflow file: {}", filePath, e);
                    throw new UncheckedIOException("Error parsing workflow file: " + filePath, e);
                }
            }
        }));
    }

    /**
     * @return {@code true} if the item was selected, {@code false} if it was ignored in favor of an existing one
     */
    private static boolean tryAddUniqueWorkflow(DiscoveredWorkflowBuildItem item, String archiveLabel,
            Map<String, DiscoveredWorkflowBuildItem> uniqueWorkflows, Map<String, String> workflowOrigins) {
        DiscoveredWorkflowBuildItem existing = uniqueWorkflows.get(item.specIdentifier());

        if (existing == null) {
            uniqueWorkflows.put(item.specIdentifier(), item);
            workflowOrigins.put(item.specIdentifier(), archiveLabel);
            return true;
        }

        if (item.fromSpec()) {
            // Allow test resources to override main resources (Maven convention)
            // The tree walk processes resources in order, so the last one wins
            uniqueWorkflows.put(item.specIdentifier(), item);
            workflowOrigins.put(item.specIdentifier(), archiveLabel);
            LOG.debug("Workflow {} found in multiple locations - using {}, overriding {}",
                    item.workflowDefinitionId(),
                    item.sourceResourcePath(),
                    existing.sourceResourcePath());
            return true;
        }

        if (existing.fromSpec()) {
            // The application's own workflow always wins over a dependency-provided one
            LOG.info("Workflow {}: application definition at '{}' overrides dependency-provided definition at '{}' ({})",
                    item.workflowDefinitionId(),
                    existing.sourceResourcePath(),
                    item.sourceResourcePath(),
                    archiveLabel);
            return false;
        }

        String existingOrigin = workflowOrigins.get(item.specIdentifier());
        if (archiveLabel.equals(existingOrigin)) {
            // Same workflow identifier defined twice inside the same dependency archive
            throw new IllegalStateException(String.format(
                    "Duplicate workflow '%s' defined twice in dependency %s: '%s' and '%s'. " +
                            "Workflow identifiers (namespace:name:version) must be unique within a dependency. " +
                            "Rename one of them, or disable dependency scanning with " +
                            "quarkus.flow.definitions.scan-dependencies=false.",
                    item.specIdentifier(),
                    archiveLabel,
                    existing.sourceResourcePath(),
                    item.sourceResourcePath()));
        }

        // Same workflow identifier contributed by two dependency archives: fail the build,
        // silently picking one would depend on classpath ordering and be non-reproducible
        throw new IllegalStateException(String.format(
                "Duplicate workflow '%s' contributed by multiple dependencies: '%s' (%s) and '%s' (%s). " +
                        "Workflow identifiers (namespace:name:version) must be unique across dependencies. " +
                        "Rename one of them, or disable dependency scanning with " +
                        "quarkus.flow.definitions.scan-dependencies=false.",
                item.specIdentifier(),
                existing.sourceResourcePath(),
                existingOrigin,
                item.sourceResourcePath(),
                archiveLabel));
    }
}
