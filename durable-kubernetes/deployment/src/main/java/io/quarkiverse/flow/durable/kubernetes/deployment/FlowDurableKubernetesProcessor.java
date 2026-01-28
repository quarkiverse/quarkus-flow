package io.quarkiverse.flow.durable.kubernetes.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class FlowDurableKubernetesProcessor {

    private static final String FEATURE = "flow-durable-kubernetes";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
