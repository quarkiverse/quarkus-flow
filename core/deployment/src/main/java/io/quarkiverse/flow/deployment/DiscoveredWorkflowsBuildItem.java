package io.quarkiverse.flow.deployment;

import java.util.HashSet;
import java.util.Set;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Holds all discovered all application's workflows
 */
public final class DiscoveredWorkflowsBuildItem extends SimpleBuildItem {

    private final Set<DiscoveredFlowBuildItem> fromSource = new HashSet<>();
    private final Set<DiscoveredWorkflowFileBuildItem> fromSpec = new HashSet<>();

    public void register(DiscoveredFlowBuildItem fromSource) {
        this.fromSource.add(fromSource);
    }

    public void register(DiscoveredWorkflowFileBuildItem fromSpec) {
        this.fromSpec.add(fromSpec);
    }

    public Set<DiscoveredFlowBuildItem> allFromSource() {
        return Set.copyOf(fromSource);
    }

    public Set<DiscoveredWorkflowFileBuildItem> allFromSpec() {
        return Set.copyOf(fromSpec);
    }
}
