package io.quarkiverse.flow.scheduler.deployment;

import io.quarkiverse.flow.scheduler.FlowScheduler;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.scheduler.deployment.ForceStartSchedulerBuildItem;

class FlowSchedulerProcessor {

    private static final String FEATURE = "flow-scheduler";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ForceStartSchedulerBuildItem forceStartScheduler() {
        return new ForceStartSchedulerBuildItem();
    }

    @BuildStep
    AdditionalBeanBuildItem registerRuntimeServices() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(FlowScheduler.class)
                .setUnremovable()
                .build();
    }
}
