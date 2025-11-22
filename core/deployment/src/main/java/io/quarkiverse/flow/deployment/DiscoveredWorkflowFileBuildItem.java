package io.quarkiverse.flow.deployment;

import java.nio.file.Path;
import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Workflow file discovered during the build.
 * <p>
 * Holds the path to the workflow file, its namespace, name, and regular identifier.
 */
public final class DiscoveredWorkflowFileBuildItem extends MultiBuildItem {

    private final Path workflowPath;
    private final String namespace;
    private final String name;
    private final String regularIdentifier;

    /**
     * Constructs a new {@link DiscoveredWorkflowFileBuildItem} instance.
     *
     * @param workflowPath Path to the workflow file
     * @param namespace Document's namespace from specification
     * @param name Document's name from specification
     */
    public DiscoveredWorkflowFileBuildItem(Path workflowPath, String namespace, String name) {
        this.workflowPath = workflowPath;
        this.namespace = namespace;
        this.name = name;
        this.regularIdentifier = namespace + ":" + name;
    }

    public String location() {
        return this.workflowPath.toString();
    }

    public String namespace() {
        return namespace;
    }

    public String name() {
        return name;
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
