package io.quarkiverse.flow.persistence.common.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.scheduler.deployment.ForceStartSchedulerBuildItem;

public class FlowPersistenceCommonProcessor {

    private static final String FEATURE = "flow-persistence-common";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ForceStartSchedulerBuildItem forceStartScheduler() {
        return new ForceStartSchedulerBuildItem();
    }

}
