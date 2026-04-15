package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emitJson;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class EmitWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("emit-event")
                .tasks(
                        emitJson("orderPlaced", "com.petstore.order.placed.v1", Message.class))
                .build();
    }
}
