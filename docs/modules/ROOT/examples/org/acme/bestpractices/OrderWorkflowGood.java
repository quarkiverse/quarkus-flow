package org.acme.bestpractices;

// tag::good[]
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class OrderWorkflowGood extends Flow {

    @Inject
    OrderService orderService;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("placeOrder")
                .tasks(
                        listen("waitOrder", toOne("order.submitted")),
                        function("placeOrder", orderService::placeOrder, Order.class)
                                .outputAs((Long id) -> id))
                .build();
    }
}
// end::good[]
