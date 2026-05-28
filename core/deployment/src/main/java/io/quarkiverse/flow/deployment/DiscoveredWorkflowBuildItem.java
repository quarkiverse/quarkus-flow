package io.quarkiverse.flow.deployment;

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
        /**
         * Workflow definition comes from a Java Source file, (e.g. a DSL descriptor).
         */
        SOURCE,
        /**
         * Workflow definition described as a YAML or JSON file in a target path.
         */
        SPEC
    }

    private String definitionResourcePath;
    private WorkflowDefinitionId workflowDefinitionId;
    private String specIdentifier;
    private String className;
    private final From from;
    private byte[] content;

    private DiscoveredWorkflowBuildItem(String definitionResourcePath, Workflow workflow, byte[] content) {
        this.definitionResourcePath = definitionResourcePath;
        this.workflowDefinitionId = WorkflowDefinitionId.of(workflow);
        this.specIdentifier = WorkflowNameUtils.yamlDescriptorIdentifier(
                workflowDefinitionId.namespace(),
                workflowDefinitionId.name(),
                workflowDefinitionId.version());
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
     * @param definitionResourcePath the classpath workflow definition resource path
     * @param workflow the parsed workflow model
     * @param content the workflow file content
     *
     * @return a new {@link DiscoveredWorkflowBuildItem}
     */
    public static DiscoveredWorkflowBuildItem fromSpec(String definitionResourcePath, Workflow workflow, byte[] content) {
        return new DiscoveredWorkflowBuildItem(definitionResourcePath, workflow, content);
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

    public String namespace() {
        return workflowDefinitionId.namespace();
    }

    public String name() {
        return workflowDefinitionId.name();
    }

    public String version() {
        return workflowDefinitionId.version();
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
     * Returns the classpath resource path for this workflow definition.
     *
     * @return the resource path (e.g., "flow/subdir/order-workflow.yaml")
     */
    public String definitionResourcePath() {
        return definitionResourcePath;
    }

    public byte[] content() {
        return content;
    }
}
