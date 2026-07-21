package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.dsl.FlowDSL.function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class GenericAgenticWorkflow extends Flow {

    @Inject
    GenericAgentic genericAgentic;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("genericAgent")
                .tasks(function("interactWithGenericAgent", genericAgentic::sendMessage, String.class))
                .build();
    }
}
