package org.acme.flow;

import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@Path("/api")
@ApplicationScoped
public class OrderResource {

    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);

    @Inject
    OrderFulfillmentWorkflow workflow;

    @Inject
    WorkflowWebSocket webSocket;

    @POST
    @Path("/orders")
    public Response order(OrderRequest request) {

        String orderId = request.orderId();

        // Broadcast order started
        broadcast("{\"orderId\":\"%s\",\"status\":\"started\",\"message\":\"Order processing started\"}", orderId);

        try {
            WorkflowInstance instance = workflow.instance(orderId);

            CompletableFuture<WorkflowModel> future = instance.start();

            future.thenAccept(model -> {
                var output = model.as(OrderFulfillmentWorkflow.WorkflowOutput.class).orElseThrow();
                if ("error".equals(output.status())) {
                    broadcast(
                            "{\"orderId\":\"%s\",\"status\":\"failed\",\"message\":\"Compensation completed: Order cancelled\"}",
                            orderId);
                } else {
                    broadcast("{\"orderId\":\"%s\",\"status\":\"completed\",\"message\":\"Order fulfilled successfully\"}",
                            orderId);
                }

            }).exceptionally(throwable -> {
                log.error("Order failed: {}", orderId, throwable);
                broadcast("{\"orderId\":\"%s\",\"status\":\"failed\",\"message\":\"Compensation completed: Order cancelled\"}",
                        orderId);
                return null;
            });

            return Response.accepted()
                    .header("Location", "/orders/" + orderId)
                    .build();
        } catch (Exception e) {
            log.error("Failed to start workflow for order: {}", orderId, e);
            broadcast("{\"orderId\":\"%s\",\"status\":\"failed\",\"message\":\"Failed to start order processing\"}", orderId);
            return Response.serverError().build();
        }
    }

    private void broadcast(String format, String orderId) {
        webSocket.broadcast(String.format(
                format,
                orderId));
    }

    public record OrderRequest(String orderId) {
    }

}