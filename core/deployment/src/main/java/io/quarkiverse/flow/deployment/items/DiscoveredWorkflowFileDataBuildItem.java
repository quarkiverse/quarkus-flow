package io.quarkiverse.flow.deployment.items;

import java.nio.file.Path;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Workflow file data discovered during the build.
 * <p>
 * Holds the path to the workflow file, its namespace, and name.
 */
public final class DiscoveredWorkflowFileDataBuildItem extends MultiBuildItem {

    private final Path workflowPath;
    private final String namespace;
    private final String name;
    private final String identifier;

    public DiscoveredWorkflowFileDataBuildItem(Path workflowPath, String namespace, String name) {
        this.workflowPath = workflowPath;
        this.namespace = namespace;
        this.name = name;
        this.identifier = namespace + ":" + name;
    }

    public String locationString() {
        return this.workflowPath.toString();
    }

    public String namespace() {
        return namespace;
    }

    public String name() {
        return name;
    }

    public String identifier() {
        return identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        DiscoveredWorkflowFileDataBuildItem that = (DiscoveredWorkflowFileDataBuildItem) o;
        return Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }
}
