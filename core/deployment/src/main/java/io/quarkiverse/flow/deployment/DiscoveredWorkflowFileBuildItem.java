package io.quarkiverse.flow.deployment;

import java.nio.file.Path;
import java.util.Objects;

import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkus.builder.item.MultiBuildItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Workflow file discovered during the build.
 * <p>
 * Holds the path to the workflow file, its namespace, name, and regular identifier.
 */
public final class DiscoveredWorkflowFileBuildItem extends MultiBuildItem {

    private final Path workflowPath;
    private final WorkflowDefinitionId workflowDefinitionId;
    private final String regularIdentifier;

    /**
     * Constructs a new {@link DiscoveredWorkflowFileBuildItem} instance.
     *
     * @param workflowPath Path to the workflow file
     * @param workflow {@link Workflow} instance representing the workflow
     */
    public DiscoveredWorkflowFileBuildItem(Path workflowPath, Workflow workflow) {
        this.workflowPath = workflowPath;

        this.workflowDefinitionId = WorkflowDefinitionId.of(workflow);
        this.regularIdentifier = WorkflowNameUtils.yamlDescriptorIdentifier(workflowDefinitionId.namespace(),
                workflowDefinitionId.name());
    }

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        DiscoveredWorkflowFileBuildItem that = (DiscoveredWorkflowFileBuildItem) o;
        return Objects.equals(regularIdentifier, that.regularIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regularIdentifier);
    }
}
