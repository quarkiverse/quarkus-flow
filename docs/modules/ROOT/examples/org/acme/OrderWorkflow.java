package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class OrderWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("orderWorkflow")
                .tasks(
                        call("queueNotification", http("/outbox/notifications")
                                .method("POST")
                                .body(Map.of("to", "${.customer.email}", "subject", "Order confirmed", "orderId", "${.id}"))))
                .build();
    }
}
