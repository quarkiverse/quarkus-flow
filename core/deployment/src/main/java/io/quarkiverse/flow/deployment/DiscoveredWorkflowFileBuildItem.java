package io.quarkiverse.flow.deployment;

import java.nio.file.Path;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Workflow file discovered during the build.
 * <p>
 * Holds the path to the workflow file, its namespace, and name.
 */
public final class DiscoveredWorkflowFileBuildItem extends MultiBuildItem {

    private final Path workflowPath;
    private final String namespace;
    private final String name;
    private final String identifier;

    public DiscoveredWorkflowFileBuildItem(Path workflowPath, String namespace, String name) {
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
        DiscoveredWorkflowFileBuildItem that = (DiscoveredWorkflowFileBuildItem) o;
        return Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }
}
