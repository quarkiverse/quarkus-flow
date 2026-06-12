package io.quarkiverse.flow.langchain4j.it.schedulable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.langchain4j.it.AgenticListener;
import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication;

/**
 * Used for customizing the WorkflowApplication to register events for testing.
 */
@Unremovable
@ApplicationScoped
public class AgenticCustomizer implements WorkflowApplicationBuilderCustomizer {

    @Inject
    AgenticListener listener;

    @Override
    public void customize(WorkflowApplication.Builder builder) {
        builder.withListener(listener);
    }
}
