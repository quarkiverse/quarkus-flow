package io.quarkiverse.flow.quartz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication;

@ApplicationScoped
@Unremovable
public class FlowQuartzApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    @Inject
    Instance<FlowQuartz> scheduler;

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        if (scheduler.isResolvable()) {
            builder.withScheduler(scheduler.get());
        }
    }
}
