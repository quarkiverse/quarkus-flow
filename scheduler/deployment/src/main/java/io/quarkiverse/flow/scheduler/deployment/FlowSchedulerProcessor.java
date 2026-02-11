package io.quarkiverse.flow.scheduler.deployment;

import io.quarkiverse.flow.deployment.WorkflowApplicationBuilderBuildItem;
import io.quarkiverse.flow.scheduler.FlowSchedulerRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class FlowSchedulerProcessor {

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    public void addFlowScheduler(FlowSchedulerRecorder recorder, WorkflowApplicationBuilderBuildItem builderItem) {
        recorder.addFlowScheduler(builderItem.builder());
    }
}
