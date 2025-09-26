package io.quarkiverse.flow.deployment;

import org.jboss.jandex.MethodInfo;

import io.quarkiverse.flow.recorders.DiscoveredFlow;
import io.quarkus.builder.item.MultiBuildItem;

public final class DiscoveredFlowBuildItem extends MultiBuildItem {
    public final DiscoveredFlow workflow;

    public DiscoveredFlowBuildItem(MethodInfo method, String workflowName) {
        this.workflow = new DiscoveredFlow(
                method.declaringClass().name().toString(),
                method.name(),
                workflowName,
                method.parameterTypes().stream().map(t -> t.name().toString()).toArray(String[]::new),
                method.isStaticInitializer());
    }

}
