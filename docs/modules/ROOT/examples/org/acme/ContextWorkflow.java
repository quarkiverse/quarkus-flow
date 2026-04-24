package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.withContext;
import static io.serverlessworkflow.fluent.spec.WorkflowBuilder.workflow;

import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class ContextWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("context-aware")
                .tasks(
                    withContext((p, c) -> {
                        System.out.println("Instance ID: " + c.instanceData().id());
                        return "Processed " + p;
                    }, String.class)
                            //.exportAsTaskOutput()
                )
                .build();
    }
}
