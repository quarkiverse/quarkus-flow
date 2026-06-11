package io.quarkiverse.flow.persistence.mvstore.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class FlowPersistenceMVstoreProcessor {

    private static final String FEATURE = "flow-persistence-mvstore";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

}
