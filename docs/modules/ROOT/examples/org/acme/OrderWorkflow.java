package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class OrderWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("orderWorkflow")
                .tasks(
                        call("queueNotification", http("/outbox/notifications")
                                .method("POST")
                                .body(Map.of("to", "${.customer.email}", "subject", "Order confirmed", "orderId", "${.id}"))))
                .build();
    }
}
