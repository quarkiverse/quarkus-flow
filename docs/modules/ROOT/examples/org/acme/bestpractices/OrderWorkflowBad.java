package org.acme.bestpractices;

// tag::bad[]
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class OrderWorkflowBad extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("placeOrder")
                .tasks(
                        listen("waitOrder", toOne("order.submitted")),
                        function("placeOrder", (OrderRequest request) -> {
                            // WARNING: This will fail with "context not active" if the
                            // workflow runs after the original HTTP request has completed.
                            QuarkusTransaction.begin();
                            try {
                                Order order = new Order(request.product(), request.quantity());
                                order.persist();
                                QuarkusTransaction.commit();
                                return order.id;
                            } catch (Exception e) {
                                QuarkusTransaction.rollback();
                                throw e;
                            }
                        }, OrderRequest.class))
                .build();
    }
}
// end::bad[]
