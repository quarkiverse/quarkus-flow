package org.acme;

import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.impl.WorkflowApplication;
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
    WorkflowApplication app;

    @POST
    @Path("/simple")
    @Blocking
    public Uni<Message> simple() throws InterruptedException {
        WorkflowInstance instance = simpleDelayedWorkflow.instance();
        CompletableFuture<WorkflowModel> start = instance.start();

        instance.suspend();

        Thread.sleep(3000);

        instance.resume();

        return Uni.createFrom().completionStage(start.toCompletableFuture())
                .onItem()
                .transform(workflowModel -> workflowModel.as(Message.class).orElseThrow());
    }

    @POST
    @Path("/faulted")
    public Uni<Void> faulted() {
        return faultedWorkflow.startInstance().onItem().transformToUni(workflowModel -> Uni.createFrom().voidItem());
    }

    @POST
    @Path("/retryable")
    public Uni<Void> retryable() {
        return retryable.startInstance().onItem().transformToUni(workflowModel -> Uni.createFrom().voidItem());
    }

    @POST
    @Path("/wait-event")
    public Uni<Void> waitEvent() {
        eventWorkflow.startInstance().onItem().transformToUni(workflowModel -> Uni.createFrom().item(workflowModel))
                .subscribe().with(workflowModel -> {
                    Log.info("GET /wait-event finalized successfully: " + workflowModel);
                }, Log::error);

        return Uni.createFrom().voidItem();
    }

    @POST
    @Path("/send-event")
    public Uni<Void> sendEvent() {
        return emitWorkflow.startInstance(Map.of("message", "Emitted from Quarkus Flow")).onItem()
                .transformToUni(workflowModel -> Uni.createFrom().voidItem());
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
