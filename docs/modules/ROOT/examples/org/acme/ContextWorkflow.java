package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.withContext;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowContextData;

@ApplicationScoped
public class ContextWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("context-aware")
                .tasks(
                        withContext((String input, WorkflowContextData contextData) -> {
                            System.out.println("Instance ID: " + contextData.instanceData().id());
                            return "Processed " + input;
                        }, String.class))
                .build();
    }
}
