package org.acme.ssm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.acme.ssm.domain.OrderRequest;
import org.acme.ssm.domain.PaymentEvent;
import org.acme.ssm.domain.ShipmentEvent;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Drives the order lifecycle state machine:
 * <ul>
 * <li>{@code POST /orders} starts an instance (enters AWAITING_PAYMENT).</li>
 * <li>{@code POST /orders/{id}/payment} emits the PAYMENT event.</li>
 * <li>{@code POST /orders/{id}/shipment} emits the SHIPMENT event.</li>
 * </ul>
 * Events are published as CloudEvents on the {@code flow-in} channel. The
 * {@code flowinstanceid} extension correlates each event to its paused instance.
 */
@Path("/orders")
public class OrderResource {

    @Inject
    OrderLifecycleWorkflow workflow;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("flow-in-outgoing")
    Emitter<String> flowIn;

    @POST
    public Response startOrder(OrderRequest request) {
        WorkflowInstance instance = workflow.instance(request);
        instance.start();
        return Response.accepted(Map.of("instanceId", instance.id())).build();
    }

    @POST
    @Path("/{instanceId}/payment")
    public Response sendPayment(@PathParam("instanceId") String instanceId, PaymentEvent payment)
            throws JsonProcessingException {
        emit(instanceId, OrderLifecycleWorkflow.PAYMENT_RECEIVED, payment);
        return Response.accepted().build();
    }

    @POST
    @Path("/{instanceId}/shipment")
    public Response sendShipment(@PathParam("instanceId") String instanceId, ShipmentEvent shipment)
            throws JsonProcessingException {
        emit(instanceId, OrderLifecycleWorkflow.SHIPMENT_DISPATCHED, shipment);
        return Response.accepted().build();
    }

    private void emit(String instanceId, String type, Object payload) throws JsonProcessingException {
        String body = objectMapper.writeValueAsString(payload);
        OutgoingCloudEventMetadata<?> ceMeta = OutgoingCloudEventMetadata.builder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("api:/orders"))
                .withType(type)
                .withDataContentType("application/json")
                .withExtension("flowinstanceid", instanceId)
                .build();
        flowIn.send(Message.of(body).addMetadata(ceMeta));
    }
}
