package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.withContext;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowContextData;

@ApplicationScoped
public class ContextWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("context-aware")
                .tasks(
                        withContext((String input, WorkflowContextData contextData) -> {
                            System.out.println("Instance ID: " + contextData.instanceData().id());
                            return "Processed " + input;
                        }, String.class))
                .build();
    }
}
