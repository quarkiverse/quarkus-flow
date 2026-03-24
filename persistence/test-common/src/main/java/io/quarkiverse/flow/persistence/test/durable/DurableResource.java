package io.quarkiverse.flow.persistence.test.durable;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;

@Path("/durable")
public class DurableResource {

    public static final AtomicInteger PRINT_DECISION_TIMES = new AtomicInteger(0);
    public static final AtomicInteger PRINT_MESSAGE_TIMES = new AtomicInteger(0);

    @Inject
    ListenWorkflow listenWorkflow;

    @Inject
    EmitWorkflow emitter;

    @GET
    @Path("/healthz")
    public String health() {
        return "ok";
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(
                new DurableResult(PRINT_MESSAGE_TIMES.get(), PRINT_DECISION_TIMES.get())).build();
    }

    @POST
    @Path("/listen")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @Blocking
    @ResponseStatus(202)
    public void execute() {
        listenWorkflow.startInstance()
                .subscribe()
                .with(ignore -> {
                });
    }

    @POST
    @Path("/emit")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @NonBlocking
    @ResponseStatus(200)
    public Uni<String> executeEmit() {
        return emitter.startInstance("hello from emitter")
                .onItem()
                .transformToUni(m -> Uni.createFrom().item(m.asText().orElseThrow()));
    }

    public record DurableResult(Integer printMessage, Integer printDecision) {
    }
}
