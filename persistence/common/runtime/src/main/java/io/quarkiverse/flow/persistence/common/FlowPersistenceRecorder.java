package io.quarkiverse.flow.persistence.common;

import jakarta.enterprise.inject.Any;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.WorkflowApplication.Builder;
import io.serverlessworkflow.impl.persistence.PersistenceApplicationBuilder;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

@Recorder
public class FlowPersistenceRecorder {
    public void addPersistenceListener(RuntimeValue<Builder> builderWrapper) {
        PersistenceApplicationBuilder.builder(builderWrapper.getValue(),
                Arc.container().select(PersistenceInstanceHandlers.class, Any.Literal.INSTANCE).get().writer());
    }
}
