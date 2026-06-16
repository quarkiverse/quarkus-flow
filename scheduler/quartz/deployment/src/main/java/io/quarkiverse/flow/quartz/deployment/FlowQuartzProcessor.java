package io.quarkiverse.flow.quartz.deployment;

import io.quarkiverse.flow.quartz.FlowQuartz;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.scheduler.deployment.ForceStartSchedulerBuildItem;

class FlowQuartzProcessor {

    private static final String FEATURE = "flow-quartz";

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
                .addBeanClass(FlowQuartz.class)
                .setUnremovable()
                .build();
    }
}
