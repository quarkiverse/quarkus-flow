package io.quarkiverse.flow.scheduler;

import jakarta.enterprise.inject.Any;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.WorkflowApplication.Builder;

@Recorder
public class FlowSchedulerRecorder {
    public void addFlowScheduler(RuntimeValue<Builder> builderWrapper) {
        builderWrapper.getValue()
                .withScheduler(
                        Arc.container().select(FlowScheduler.class, Any.Literal.INSTANCE).get());
    }
}
