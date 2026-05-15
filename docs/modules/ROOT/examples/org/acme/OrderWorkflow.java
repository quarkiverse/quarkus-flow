package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

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
                        function("chargeCustomer", input -> input, Object.class),
                        // This task writes to the outbox instead of sending the email directly
                        call("queueNotification", http("/outbox/notifications")
                                .method("POST")
                                .body("{ \"to\": .customer.email, \"subject\": \"Order confirmed\", \"orderId\": .orderId }")),
                        function("updateLedger", input -> input, Object.class))
                .build();
    }
}
