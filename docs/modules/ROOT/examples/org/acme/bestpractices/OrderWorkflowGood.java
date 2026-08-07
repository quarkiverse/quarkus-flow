package org.acme.bestpractices;

// tag::good[]
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class OrderWorkflowGood extends Flow {

    @Inject
    OrderService orderService;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("placeOrder")
                .tasks(
                        listen("waitOrder", toOne("order.submitted")),
                        function("placeOrder", orderService::placeOrder, Order.class)
                                .outputAs((Long id) -> id))
                .build();
    }
}
// end::good[]
