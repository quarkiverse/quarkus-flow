package org.acme.ssm;

import static io.quarkiverse.flow.dsl.FlowDSL.consumed;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.ssm.domain.OrderRequest;
import org.acme.ssm.domain.OrderResult;
import org.acme.ssm.domain.PaymentEvent;
import org.acme.ssm.domain.ShipmentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An order lifecycle modeled as a Quarkus Flow workflow, written as a 1:1
 * translation of the classic Spring StateMachine order example.
 *
 * <pre>
 * Spring StateMachine                         Quarkus Flow
 * ------------------------------------------  ---------------------------------
 * state NEW + entry action                    function("placeOrder")  (a call task)
 * event PAYMENT + state AWAITING_PAYMENT      listen("awaitPayment")  (event wait)
 * guard(payment.approved) + choice state      switchWhenOrElse(...)
 * state PAID + entry action                   function("fulfillOrder")
 * event SHIPPED + state AWAITING_SHIPMENT     listen("awaitShipment")
 * end state DELIVERED + entry action          function("completeOrder").then(END)
 * end state CANCELLED + entry action          function("cancelOrder").then(END)
 * </pre>
 *
 * The key idea for migrators: a Spring StateMachine <em>action</em> (code bolted
 * onto a state/transition) becomes a first-class {@code function(...)} call task
 * invoking a plain Java method. It is an explicit, testable step in the flow
 * rather than logic hidden inside state configuration.
 */
@ApplicationScoped
public class OrderLifecycleWorkflow extends Flow {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleWorkflow.class);

    static final String PAYMENT_RECEIVED = "org.acme.order.payment.received";
    static final String SHIPMENT_DISPATCHED = "org.acme.order.shipment.dispatched";

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("order-lifecycle", "examples")
                .tasks(
                        // state NEW -> entry action
                        function("placeOrder", this::placeOrder, OrderRequest.class),

                        // AWAITING_PAYMENT: pause until the PAYMENT event arrives,
                        // correlated to this instance via the flowinstanceid extension
                        listen("awaitPayment",
                                toOne(consumed(PAYMENT_RECEIVED).extensionByInstanceId("flowinstanceid"))),

                        // guard + choice: approved -> fulfillOrder, otherwise -> cancelOrder
                        switchWhenOrElse(PaymentEvent::approved,
                                "fulfillOrder", "cancelOrder", PaymentEvent.class),

                        // state PAID -> entry action
                        function("fulfillOrder", this::fulfillOrder, PaymentEvent.class),

                        // AWAITING_SHIPMENT: pause until the SHIPMENT event arrives
                        listen("awaitShipment",
                                toOne(consumed(SHIPMENT_DISPATCHED).extensionByInstanceId("flowinstanceid"))),

                        // end state DELIVERED -> entry action, then terminate
                        function("completeOrder", this::completeOrder, ShipmentEvent.class)
                                .then(FlowDirectiveEnum.END),

                        // end state CANCELLED -> entry action, then terminate
                        function("cancelOrder", this::cancelOrder, PaymentEvent.class)
                                .then(FlowDirectiveEnum.END))
                .build();
    }

    // --- Actions (Spring StateMachine actions become plain Java methods) ---

    private OrderRequest placeOrder(OrderRequest request) {
        log.info("[NEW] Order {} placed by {} for {}", request.orderId(), request.customer(), request.amount());
        return request;
    }

    private OrderResult fulfillOrder(PaymentEvent payment) {
        log.info("[PAID] Payment {} approved for order {}, fulfilling", payment.reference(), payment.orderId());
        return new OrderResult(payment.orderId(), "FULFILLING", "Payment " + payment.reference() + " approved");
    }

    private OrderResult completeOrder(ShipmentEvent shipment) {
        log.info("[DELIVERED] Order {} shipped via {} ({})",
                shipment.orderId(), shipment.carrier(), shipment.trackingId());
        return new OrderResult(shipment.orderId(), "DELIVERED",
                "Shipped via " + shipment.carrier() + ", tracking " + shipment.trackingId());
    }

    private OrderResult cancelOrder(PaymentEvent payment) {
        log.info("[CANCELLED] Payment rejected for order {}, cancelling", payment.orderId());
        return new OrderResult(payment.orderId(), "CANCELLED", "Payment rejected");
    }
}
