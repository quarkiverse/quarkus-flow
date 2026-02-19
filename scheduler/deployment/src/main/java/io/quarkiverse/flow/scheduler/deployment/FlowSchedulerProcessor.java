package io.quarkiverse.flow.scheduler.deployment;

import io.quarkiverse.flow.deployment.WorkflowApplicationBuilderBuildItem;
import io.quarkiverse.flow.scheduler.FlowScheduler;
import io.quarkiverse.flow.scheduler.FlowSchedulerRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.scheduler.deployment.ForceStartSchedulerBuildItem;

class FlowSchedulerProcessor {

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void addFlowScheduler(FlowSchedulerRecorder recorder,
            WorkflowApplicationBuilderBuildItem builderItem) {
        recorder.addFlowScheduler(builderItem.builder());

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
