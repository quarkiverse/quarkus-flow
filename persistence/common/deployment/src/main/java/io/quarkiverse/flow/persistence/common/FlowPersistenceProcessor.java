package io.quarkiverse.flow.persistence.common;

import io.quarkiverse.flow.deployment.WorkflowApplicationBuilderBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class FlowPersistenceProcessor {

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    public void fillApplicationBuilder(FlowPersistenceRecorder recorder, WorkflowApplicationBuilderBuildItem builderItem) {
        recorder.addPersistenceListener(builderItem.builder());
    }

}
