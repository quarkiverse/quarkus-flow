package io.quarkiverse.flow.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class DiscoveredFlowBuildItem extends MultiBuildItem {
    private final String className;

    public DiscoveredFlowBuildItem(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
}
