package io.quarkiverse.flow.langchain4j.recorders;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.langchain4j.spec.AgenticAwareModelFactory;
import io.quarkiverse.flow.recorders.WorkflowApplicationBuilderCustomizer;
import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.WorkflowApplication.Builder;

@ApplicationScoped
@Unremovable
public class FlowLangChain4jApplicationBuilderCustomizer implements WorkflowApplicationBuilderCustomizer {

    @Override
    public void customize(Builder builder) {
        builder.withModelFactory(new AgenticAwareModelFactory());
    }
}
