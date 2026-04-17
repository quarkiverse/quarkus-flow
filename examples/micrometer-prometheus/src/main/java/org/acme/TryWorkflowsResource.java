package org.acme;

import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Path("/try-workflows")
public class TryWorkflowsResource {
    private final Random random = new Random();

    @Inject
    SimpleWorkflow simpleDelayedWorkflow;

    @Inject
    FaultedWorkflow faultedWorkflow;

    @Inject
    EventWorkflow eventWorkflow;

    @Inject
    @Identifier("test.RetryableExample")
    Flow retryable;

    @Inject
    @Identifier("test.EmitEvent")
    Flow emitWorkflow;

    @Inject
    WorkflowWebSocket webSocket;

    @POST
    @Path("/simple")
    @Blocking
    public Uni<Message> simple() throws InterruptedException {
        WorkflowInstance instance = simpleDelayedWorkflow.instance();
        CompletableFuture<WorkflowModel> start = instance.start();

        instance.suspend();

        Thread.sleep(3000);

        instance.resume();

        return Uni.createFrom().completionStage(start.toCompletableFuture()).onItem()
                .transform(workflowModel -> {
                    Message message = workflowModel.as(Message.class).orElseThrow();
                    webSocket.broadcast(
                            "{\"type\":\"simple\",\"status\":\"completed\",\"message\":\"" + message.message() + "\"}");
                    return message;
                });
    }

    @POST
    @Path("/faulted")
    public Uni<Void> faulted() {
        return faultedWorkflow.startInstance()
                .onItem().invoke(() -> webSocket.broadcast("{\"type\":\"faulted\",\"status\":\"completed\"}"))
                .onFailure()
                .invoke(throwable -> webSocket
                        .broadcast("{\"type\":\"faulted\",\"status\":\"failed\",\"error\":\"" + throwable.getMessage() + "\"}"))
                .onItem().transformToUni(workflowModel -> Uni.createFrom().voidItem());
    }

    @POST
    @Path("/retryable")
    public Uni<Void> retryable() {
        return retryable.startInstance()
                .onItem().invoke(() -> webSocket.broadcast("{\"type\":\"retryable\",\"status\":\"completed\"}"))
                .onFailure()
                .invoke(throwable -> webSocket.broadcast(
                        "{\"type\":\"retryable\",\"status\":\"failed\",\"error\":\"" + throwable.getMessage() + "\"}"))
                .onItem().transformToUni(workflowModel -> Uni.createFrom().voidItem());
    }

    @POST
    @Path("/wait-event")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<CallForPapers> waitEvent() {
        CallForPapers cfp = CallForPapers.createMock();

        // Broadcast CFP data to WebSocket clients
        try {
            String cfpJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .writeValueAsString(cfp);
            webSocket.broadcast("{\"type\":\"wait-event\",\"status\":\"waiting\",\"callForPapers\":" + cfpJson + "}");
        } catch (Exception e) {
            Log.error("Error serializing CFP", e);
        }

        eventWorkflow.startInstance().onItem().transformToUni(workflowModel -> Uni.createFrom().item(workflowModel))
                .subscribe().with(workflowModel -> {
                    Map<String, Object> result = workflowModel.as(Map.class).orElse(Map.of());
                    Log.info("Approval decision received: " + result);

                    try {
                        String resultJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
                        webSocket.broadcast("{\"type\":\"wait-event\",\"status\":\"completed\",\"result\":" + resultJson + "}");
                    } catch (Exception e) {
                        Log.error("Error serializing result", e);
                    }
                }, throwable -> {
                    Log.error("Approval workflow failed", throwable);
                    webSocket.broadcast(
                            "{\"type\":\"wait-event\",\"status\":\"failed\",\"error\":\"" + throwable.getMessage() + "\"}");
                });

        return Uni.createFrom().item(cfp);
    }

    @POST
    @Path("/send-event")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Map<String, Object>> sendEvent(Map<String, Object> approval) {
        return emitWorkflow.startInstance(approval)
                .onItem().invoke(() -> {
                    boolean approved = (boolean) approval.getOrDefault("approved", true);
                    webSocket.broadcast("{\"type\":\"send-event\",\"status\":\"completed\",\"approved\":" + approved + "}");
                })
                .onFailure()
                .invoke(throwable -> webSocket.broadcast(
                        "{\"type\":\"send-event\",\"status\":\"failed\",\"error\":\"" + throwable.getMessage() + "\"}"))
                .onItem().transform(workflowModel -> approval);
    }

    @GET
    @Path("/pets")
    @Produces(MediaType.APPLICATION_JSON)
    public Response random500() {
        Log.info("Calling random500()");
        return random.nextBoolean()
                ? Response.status(500).entity(new Message("Quarkus Flow: You got a random error :)")).build()
                : Response.status(204).entity(new Message("Quarkus Flow: Hi there :)")).build();
    }
}
