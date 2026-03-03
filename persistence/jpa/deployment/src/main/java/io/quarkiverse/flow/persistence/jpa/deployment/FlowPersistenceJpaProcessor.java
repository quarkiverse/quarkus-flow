package io.quarkiverse.flow.persistence.jpa.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowPersistenceJpaProcessor {

    private static final String FEATURE = "flow-persistence-jpa";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
