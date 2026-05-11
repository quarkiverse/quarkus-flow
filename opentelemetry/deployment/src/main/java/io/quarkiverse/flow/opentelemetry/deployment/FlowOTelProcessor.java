package io.quarkiverse.flow.opentelemetry.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class FlowOTelProcessor {

    private static final String FEATURE = "flow-opentelemetry";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
