package io.quarkiverse.flow.deployment;

import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkus.builder.item.MultiBuildItem;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Build item representing a workflow discovered during the build phase.
 * <p>
 * A workflow can be discovered either from:
 * <ul>
 * <li>a workflow specification file (YAML/JSON)</li>
 * <li>a workflow specification file (YAML/JSON) inside a dependency JAR</li>
 * <li>application source code</li>
 * </ul>
 * <p>
 */
public final class DiscoveredWorkflowBuildItem extends MultiBuildItem {

    private final From from;
    private String definitionResourcePath;
    private String sourceResourcePath;
    private WorkflowDefinitionId workflowDefinitionId;
    private Workflow workflowFromSpec;
    private String specIdentifier;
    private String className;

    private DiscoveredWorkflowBuildItem(String definitionResourcePath, String sourceResourcePath, Workflow workflow,
            From from) {
        this.definitionResourcePath = definitionResourcePath;
        this.sourceResourcePath = sourceResourcePath;
        this.workflowDefinitionId = WorkflowDefinitionId.of(workflow);
        this.specIdentifier = WorkflowNameUtils.yamlDescriptorIdentifier(
                workflowDefinitionId.namespace(),
                workflowDefinitionId.name(),
                workflowDefinitionId.version());
        this.workflowFromSpec = workflow;
        this.from = from;
    }

    private DiscoveredWorkflowBuildItem(String className) {
        this.className = className;
        this.from = From.SOURCE;
    }

    /**
     * Creates a build item for a workflow discovered from a specification file
     * in the application's own resources (root archive).
     *
     * @param definitionResourcePath the classpath workflow definition resource path
     * @param workflow the parsed workflow model
     * @return a new {@link DiscoveredWorkflowBuildItem}
     */
    public static DiscoveredWorkflowBuildItem fromSpec(String definitionResourcePath, Workflow workflow) {
        return new DiscoveredWorkflowBuildItem(definitionResourcePath, definitionResourcePath, workflow, From.SPEC);
    }

    /**
     * Creates a build item for a workflow discovered from a specification file
     * inside a dependency JAR.
     * <p>
     * Different dependencies may ship files with the same classpath path (e.g. {@code flow/shared.yaml}),
     * and the classpath only exposes one of them. Dependency-provided definitions are therefore loaded at
     * runtime from a unique, build-generated copy instead of their original location.
     *
     * @param sourceResourcePath the original path of the definition inside the dependency JAR
     * @param generatedResourcePath the unique, build-generated classpath resource path used at runtime
     * @param workflow the parsed workflow model
     * @return a new {@link DiscoveredWorkflowBuildItem}
     */
    public static DiscoveredWorkflowBuildItem fromDependencySpec(String sourceResourcePath, String generatedResourcePath,
            Workflow workflow) {
        return new DiscoveredWorkflowBuildItem(generatedResourcePath, sourceResourcePath, workflow, From.DEPENDENCY_SPEC);
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
     *         in the application's own resources
     */
    public boolean fromSpec() {
        return From.SPEC == this.from;
    }

    /**
     * @return {@code true} if the workflow was discovered from a specification file
     *         inside a dependency JAR
     */
    public boolean fromDependencySpec() {
        return From.DEPENDENCY_SPEC == this.from;
    }

    /**
     * @return {@code true} if the workflow was discovered from a specification file (YAML/JSON),
     *         either in the application's own resources or inside a dependency JAR
     */
    public boolean fromSpecFile() {
        return fromSpec() || fromDependencySpec();
    }

    /**
     * Returns the classpath resource path for this workflow definition.
     * <p/>
     * May be used to read a workflow definition, usually with {@link WorkflowReader}.
     * For {@link From#DEPENDENCY_SPEC} workflows, this is the unique build-generated copy of the definition.
     *
     * @return the resource path (e.g., "flow/subdir/order-workflow.yaml")
     */
    public String definitionResourcePath() {
        return definitionResourcePath;
    }

    /**
     * Returns the original location of the definition file, as found in the application or dependency archive.
     * Equal to {@link #definitionResourcePath()} for {@link From#SPEC} workflows.
     *
     * @return the original resource path (e.g., "flow/subdir/order-workflow.yaml"), null for source workflows
     */
    public String sourceResourcePath() {
        return sourceResourcePath;
    }

    /**
     * @return Workflow descriptor parsed from {@link From#SPEC} or {@link From#DEPENDENCY_SPEC}, null otherwise.
     */
    public Workflow workflowFromSpec() {
        return workflowFromSpec;
    }

    private enum From {
        /**
         * Workflow definition comes from a Java Source file, (e.g. a DSL descriptor).
         */
        SOURCE,
        /**
         * Workflow definition described as a YAML or JSON file in a target path.
         */
        SPEC,
        /**
         * Workflow definition described as a YAML or JSON file inside a dependency JAR.
         */
        DEPENDENCY_SPEC
    }
}
