package io.quarkiverse.flow.devui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.serverlessworkflow.impl.WorkflowApplication;

@ApplicationScoped
public class RuntimeDevApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        final ArcContainer container = Arc.container();
        builder.withListener(container.select(ManagementLifecycleListener.class, new Any.Literal()).get());
    }
}
