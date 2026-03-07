package io.quarkiverse.flow.devui;

import jakarta.enterprise.inject.Any;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.WorkflowApplication;

@Recorder
public class InjectManagementListenerRecorder {

    public void addManagementLifecycleListener(RuntimeValue<WorkflowApplication.Builder> builder) {
        final ArcContainer container = Arc.container();
        builder.getValue().withListener(container.select(ManagementLifecycleListener.class, new Any.Literal()).get());
    }
}
