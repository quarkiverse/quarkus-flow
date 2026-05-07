package io.quarkiverse.flow.recorders;

import java.util.function.Function;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.WorkflowApplication;

@Recorder
public class WorkflowApplicationRecorder {

    public Function<SyntheticCreationalContext<WorkflowApplication>, WorkflowApplication> workflowAppCreator(
            ShutdownContext shutdownContext, boolean tracingEnabled, boolean isMicrometerSupported) {
        return context -> {
            WorkflowApplicationCreator creator = context.getInjectedReference(WorkflowApplicationCreator.class);
            WorkflowApplication app = creator.create(tracingEnabled, isMicrometerSupported);
            shutdownContext.addShutdownTask(app::close);
            return app;
        };
    }
}
