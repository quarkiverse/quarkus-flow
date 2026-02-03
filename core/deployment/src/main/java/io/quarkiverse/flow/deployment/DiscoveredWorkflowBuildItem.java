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

    private Path workflowPath;
    private WorkflowDefinitionId workflowDefinitionId;
    private String regularIdentifier;
    private String className;
    private final From from;

    private DiscoveredWorkflowBuildItem(Path workflowPath, Workflow workflow) {
        this.workflowPath = workflowPath;
        this.workflowDefinitionId = WorkflowDefinitionId.of(workflow);
        this.regularIdentifier = WorkflowNameUtils.yamlDescriptorIdentifier(
                workflowDefinitionId.namespace(),
                workflowDefinitionId.name());
        this.from = From.SPEC;
    }

    private DiscoveredWorkflowBuildItem(String className) {
        this.className = className;
        this.from = From.SOURCE;
    }

    /**
     * Creates a build item for a workflow discovered from a specification file.
     *
     * @param workflowPath the path to the workflow specification file
     * @param workflow the parsed workflow model
     * @return a new {@link DiscoveredWorkflowBuildItem}
     */
    public static DiscoveredWorkflowBuildItem isFromSpec(Path workflowPath, Workflow workflow) {
        return new DiscoveredWorkflowBuildItem(workflowPath, workflow);
    }

    /**
     * Creates a build item for a workflow discovered from application source code.
     *
     * @param className the fully qualified name of the workflow class
     * @return a new {@link DiscoveredWorkflowBuildItem}
     */
    public static DiscoveredWorkflowBuildItem isFromSource(String className) {
        return new DiscoveredWorkflowBuildItem(className);
    }

    /**
     * Returns the location of the workflow specification on disk.
     *
     * @return the workflow file location
     */
    public String location() {
        return this.workflowPath.toString();
    }

    public String namespace() {
        return workflowDefinitionId.namespace();
    }

    public String name() {
        return workflowDefinitionId.name();
    }

    public String regularIdentifier() {
        return regularIdentifier;
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
    public boolean isFromSource() {
        return From.SOURCE == this.from;
    }

    /**
     * @return {@code true} if the workflow was discovered from a specification file
     */
    public boolean isFromSpec() {
        return From.SPEC == this.from;
    }
}
