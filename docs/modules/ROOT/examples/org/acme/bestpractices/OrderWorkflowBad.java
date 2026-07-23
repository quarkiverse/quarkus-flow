package org.acme.bestpractices;

// tag::bad[]
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class OrderWorkflowBad extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("placeOrder")
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
