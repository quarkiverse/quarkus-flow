package io.quarkiverse.flow.deployment;

import java.nio.file.Path;

import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkus.builder.item.MultiBuildItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Build item representing a workflow discovered during the build phase.
 * <p>
 * A workflow can be discovered either from:
 * <ul>
 * <li>a workflow specification file (YAML/JSON)</li>
 * <li>application source code</li>
 * </ul>
 * <p>
 */
public final class DiscoveredWorkflowBuildItem extends MultiBuildItem {

    private enum From {
        SOURCE,
        SPEC
    }

    private Path absolute;
    private Path relativeToFlowDir;
    private WorkflowDefinitionId workflowDefinitionId;
    private String specIdentifier;
    private String className;
    private final From from;
    private byte[] content;

    private DiscoveredWorkflowBuildItem(Path absolute, Path relativeToFlowDir, Workflow workflow, byte[] content) {
        this.absolute = absolute;
        this.relativeToFlowDir = relativeToFlowDir;
        this.workflowDefinitionId = WorkflowDefinitionId.of(workflow);
        this.specIdentifier = WorkflowNameUtils.yamlDescriptorIdentifier(
                workflowDefinitionId.namespace(),
                workflowDefinitionId.name());
        this.content = content;
        this.from = From.SPEC;
    }

    private DiscoveredWorkflowBuildItem(String className) {
        this.className = className;
        this.from = From.SOURCE;
    }

    /**
     * Creates a build item for a workflow discovered from a specification file.
     *
     * @param absolute the path to the workflow specification file
     * @param workflow the parsed workflow model
     * @param relativeToFlowDir the relative path from the flow directory
     * @param content the workflow file content
     *
     * @return a new {@link DiscoveredWorkflowBuildItem}
     */
    public static DiscoveredWorkflowBuildItem fromSpec(Path absolute, Path relativeToFlowDir, Workflow workflow,
            byte[] content) {
        return new DiscoveredWorkflowBuildItem(absolute, relativeToFlowDir, workflow, content);
    }

    /**
     * Creates a build item for a workflow discovered from application source code.
     *
     * @param className the fully qualified name of the workflow class
     * @return a new {@link DiscoveredWorkflowBuildItem}
     */
    public static DiscoveredWorkflowBuildItem fromSource(String className) {
        return new DiscoveredWorkflowBuildItem(className);
    }

    /**
     * Returns the absolute location of the workflow specification on disk.
     *
     * @return the workflow file location
     */
    public String absolutePath() {
        return this.absolute.toString();
    }

    public String namespace() {
        return workflowDefinitionId.namespace();
    }

    public String name() {
        return workflowDefinitionId.name();
    }

    public String specIdentifier() {
        return specIdentifier;
    }

    public WorkflowDefinitionId workflowDefinitionId() {
        return workflowDefinitionId;
    }

    /**
     * Returns the fully qualified workflow class name.
     *
     * @return the workflow class name
     */
    public String className() {
        return className;
    }

    /**
     * @return {@code true} if the workflow was discovered from source code
     */
    public boolean fromSource() {
        return From.SOURCE == this.from;
    }

    /**
     * @return {@code true} if the workflow was discovered from a specification file
     */
    public boolean fromSpec() {
        return From.SPEC == this.from;
    }

    /**
     * Returns the relative path of the workflow specification file from the flow directory.
     * <p>
     * This is used for test resource override: a test workflow file overrides
     * a main workflow file only if they have the same relative path.
     *
     * @return the relative flow path, or {@code null} if discovered from source
     */
    public String relativeToFlowDir() {
        return relativeToFlowDir.toString();
    }

    public byte[] content() {
        return content;
    }
}
