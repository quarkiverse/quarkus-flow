package org.acme.flow;

import io.quarkiverse.flow.Flow;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncCallStep;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.tasks;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.tryCatch;

@ApplicationScoped
public class OrderFulfillmentWorkflow extends Flow {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentWorkflow.class.getName());

    private static final String STOCK_ORDER_ERROR = "ERR_001";
    private static final String PAYMENT_PROCESSING_ERROR = "ERR_002";
    private static final String SHIPPING_ERROR = "ERR_003";

    private static final String ORDER_001 = "ORDER#001";
    private static final String ORDER_002 = "ORDER#002";
    private static final String ORDER_003 = "ORDER#003";
    private static final String END_FLOW = "endFlow";

    @Inject
    WorkflowWebSocket webSocket;

    @Override
    public Workflow descriptor() {

        FuncCallStep<OrderStep, WorkflowOutput> cancelStock = function("cancelStock",
                (OrderStep o) -> cancelReservation(o.orderId()));
        FuncCallStep<OrderStep, OrderStep> cancelPayment = function("cancelPayment",
                (OrderStep o) -> cancelPayment(o.orderId()));

        return FuncWorkflowBuilder.workflow("order-fulfillment", "examples")
                .tasks(
                        tryCatch(
                                "tryStockReservation",
                                t -> t.tryCatch(function("stockReservation", this::reserveStock))
                                        .catchError(
                                                err -> err.type(STOCK_ORDER_ERROR),
                                                function("notifyStockFailure", this::notifyStockFailure)
                                                        .then(FlowDirectiveEnum.END))),
                        tryCatch(
                                "tryPaymentProcessing",
                                t -> t.tryCatch(function("paymentProcessing", this::processPayment))
                                        .catchWhen(
                                                "${ .status == 503 }",
                                                cancelStock.then(FlowDirectiveEnum.END))),
                        tryCatch(
                                "tryShipping",
                                t -> t.tryCatch(function("scheduleShipping", this::scheduleShipping))
                                        .catchType(
                                                SHIPPING_ERROR,
                                                tasks(
                                                        cancelPayment,
                                                        cancelStock))))
                .build();
    }

    private OrderStep reserveStock(String order) {
        log.info("Reserving stock for order: {}", order);
        broadcastStep(order, "stockReservation", "processing", "Reserving stock...");
        waitMs(500);
        if (order.equals(ORDER_001)) {
            broadcastStep(order, "stockReservation", "failed", "Stock reservation failed - Out of stock");
            throw new WorkflowException(WorkflowError.error(STOCK_ORDER_ERROR, 409).build());
        }
        broadcastStep(order, "stockReservation", "completed", "Stock reserved successfully");
        return new OrderStep(order, "reserved");
    }

    private OrderStep processPayment(OrderStep order) {
        log.info("Processing payment for order: {}", order);
        broadcastStep(order.orderId(), "paymentProcessing", "processing", "Processing payment...");
        waitMs(800);
        if (ORDER_002.equals(order.orderId())) {
            broadcastStep(order.orderId(), "paymentProcessing", "failed", "Payment processing failed - Service unavailable");
            throw new WorkflowException(WorkflowError.error(PAYMENT_PROCESSING_ERROR, 503).build());
        }
        broadcastStep(order.orderId(), "paymentProcessing", "completed", "Payment processed successfully");
        return new OrderStep(order.orderId(), "paid");
    }

    private OrderStep scheduleShipping(OrderStep order) {
        log.info("Scheduling shipping for order: {}", order);
        broadcastStep(order.orderId(), "shipping", "processing", "Scheduling shipping...");
        waitMs(800);
        if (ORDER_003.equals(order.orderId())) {
            broadcastStep(order.orderId(), "shipping", "failed", "Shipping failed - Carrier unavailable");
            throw new WorkflowException(WorkflowError.error(SHIPPING_ERROR, 500).build());
        }
        broadcastStep(order.orderId(), "shipping", "completed", "Shipping scheduled successfully");
        return new OrderStep(order.orderId(), "shipping");
    }

    private WorkflowOutput notifyStockFailure(String order) {
        log.info("Stock reservation failed for order: {}, there is nothing to compensate", order);
        broadcastStep(order, "compensation", "failed", "Stock unavailable — no reservation to cancel");
        return new WorkflowOutput("error");
    }

    private WorkflowOutput cancelReservation(String order) {
        log.info("Cancelling stock reservation for order: {}", order);
        broadcastStep(order, "compensation", "processing", "Cancelling stock reservation...");
        waitMs(400);
        broadcastStep(order, "compensation", "completed", "Stock reservation cancelled");
        return new WorkflowOutput("error");
    }

    private OrderStep cancelPayment(String order) {
        log.info("Cancel payment for order: {}", order);
        broadcastStep(order, "compensation", "processing", "Cancelling payment...");
        waitMs(400);
        broadcastStep(order, "compensation", "completed", "Payment cancelled");
        return new OrderStep(order, "error");
    }

    private void broadcastStep(String orderId, String step, String status, String message) {
        String json = String.format(
                "{\"orderId\":\"%s\",\"step\":\"%s\",\"status\":\"%s\",\"message\":\"%s\"}",
                orderId, step, status, message);
        webSocket.broadcast(json);
    }

    private static void waitMs(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @RegisterForReflection
    public record OrderStep(String orderId, String status) {
    }

    @RegisterForReflection
    public record WorkflowOutput(String status) {
    }
}
