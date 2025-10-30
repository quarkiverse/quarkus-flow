package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class GenericAgenticWorkflow extends Flow {

    @Inject
    GenericAgentic genericAgentic;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("genericAgent")
                .tasks(t -> t.callFn("interactWithGenericAgent", f -> f.function(genericAgentic::sendMessage)))
                .build();
    }
}
