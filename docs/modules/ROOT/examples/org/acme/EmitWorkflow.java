package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.emitJson;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class EmitWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("emit-event-workflow", "org.acme", "1.0")
                .tasks(
                        emitJson("orderPlaced", "com.petstore.order.placed.v1", Message.class))
                .build();
    }
}
