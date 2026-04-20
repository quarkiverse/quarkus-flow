package io.quarkiverse.flow.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowDefinitionsConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;

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

    /**
     * Resolves the flow resource path based on the configured path and whether we want test or main resources.
     * <p>
     * The configured path (e.g., {@code src/main/flow}) is relative to main resources.
     * When {@code isTestResources=true}, the equivalent test resources path is computed
     * by replacing {@code src/main/} with {@code src/test/} (e.g., {@code src/test/flow}).
     *
     * @param outputDir the output directory from OutputTargetBuildItem
     * @param flowDir the configured flow directory path (relative to main resources)
     * @param launchMode the current launch mode to determine if we are in test mode
     * @return the resolved flow resource path
     */
    private static Path resolveFlowResourceDir(Path outputDir, Path flowDir, LaunchMode launchMode) {
        if (flowDir.isAbsolute()) {
            return flowDir;
        }
        Path moduleRoot = findModuleRootFromTarget(outputDir);

        if (launchMode == LaunchMode.TEST) {
            // Convert main resources path to test resources path
            // e.g., src/main/flow -> src/test/flow
            //       src/main/workflows/custom -> src/test/workflows/custom
            Path flowDirNormalized = flowDir.normalize();
            Path srcMain = Paths.get("src", "main");
            if (flowDirNormalized.startsWith(srcMain)) {
                Path relative = srcMain.relativize(flowDirNormalized);
                return moduleRoot.resolve(Paths.get("src", "test").resolve(relative)).normalize();
            }
        }

        return moduleRoot.resolve(flowDir).normalize();
    }

    private static List<DiscoveredWorkflowBuildItem> collectWorkflowFileData(Path flowDir) {
        List<DiscoveredWorkflowBuildItem> items = new ArrayList<>();

        try (var stream = Files.walk(flowDir)) {
            stream.filter(file -> Files.isRegularFile(file) && SUPPORTED_WORKFLOW_FILE_EXTENSIONS.stream()
                    .anyMatch(ext -> file.getFileName().toString().endsWith(ext)))
                    .forEach(file -> {
                        try {
                            Workflow workflow = WorkflowReader.readWorkflow(file);
                            items.add(
                                    DiscoveredWorkflowBuildItem.fromSpec(file, workflow, flowDir.relativize(file).toString()));
                        } catch (IOException e) {
                            LOG.error("Failed to parse workflow file at path: {}", file, e);
                            throw new UncheckedIOException("Error while parsing workflow file: " + file, e);
                        }
                    });
        } catch (IOException e) {
            LOG.error("Failed to scan flow resources in path: {}", flowDir, e);
            throw new UncheckedIOException(
                    "Error while scanning flow resources in path: " + flowDir, e);
        }
        return items;
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
            wf.produce(DiscoveredWorkflowBuildItem.fromSource(flow.name().toString()));
        }
    }

    /**
     * Collect all workflow files from the specified flow directory and produce
     * build items for each unique workflow.
     */
    @BuildStep
    public void collectUniqueWorkflowFileData(OutputTargetBuildItem outputTarget,
            LaunchModeBuildItem launchMode,
            FlowDefinitionsConfig flowDefinitionsConfig,
            BuildProducer<DiscoveredWorkflowBuildItem> workflows) {

        final Path flowDir = Paths.get(flowDefinitionsConfig.dir().orElse(FlowDefinitionsConfig.DEFAULT_FLOW_DIR));

        Map<String, DiscoveredWorkflowBuildItem> workflowsMap = new HashMap<>();
        Set<String> collectedInTest = new HashSet<>();

        if (launchMode.getLaunchMode() == LaunchMode.TEST) {
            Path testFlowDir = resolveFlowResourceDir(
                    outputTarget.getOutputDirectory(), flowDir, launchMode.getLaunchMode());

            if (Files.exists(testFlowDir)) {
                for (DiscoveredWorkflowBuildItem item : collectWorkflowFileData(testFlowDir)) {
                    tryAddUniqueWorkflow(item, workflowsMap);
                    collectedInTest.add(item.relativeFlowPath());
                }
            }
        }

        // Scan main resources (lower priority - skip files that exist in test resources with same relative path)
        Path mainFlowDir = resolveFlowResourceDir(
                outputTarget.getOutputDirectory(), flowDir, LaunchMode.NORMAL);

        if (Files.exists(mainFlowDir)) {
            for (DiscoveredWorkflowBuildItem collectedInMain : collectWorkflowFileData(mainFlowDir)) {
                if (skipIfSameRelativePath(collectedInMain, collectedInTest)) {
                    LOG.debug("Skipping workflow from src/main/* {} (overridden by test resource with same relative path)",
                            collectedInMain.location());
                    continue;
                }

                tryAddUniqueWorkflow(collectedInMain, workflowsMap);
            }
        }

        workflowsMap.values().forEach(workflows::produce);
    }

    private static boolean skipIfSameRelativePath(DiscoveredWorkflowBuildItem collectedInMain, Set<String> collectedInTest) {
        return collectedInTest.contains(collectedInMain.relativeFlowPath());
    }

    private static void tryAddUniqueWorkflow(DiscoveredWorkflowBuildItem item,
            Map<String, DiscoveredWorkflowBuildItem> uniqueWorkflows) {
        String identifier = item.regularIdentifier();
        if (uniqueWorkflows.put(identifier, item) != null) {
            throw new IllegalStateException(String.format(
                    "Duplicate workflow detected %s", item.workflowDefinitionId()));
        }
    }
}
