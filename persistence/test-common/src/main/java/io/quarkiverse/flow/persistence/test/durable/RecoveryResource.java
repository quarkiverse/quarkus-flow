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

@Path("/durable/recovery")
public class RecoveryResource {

    public static final AtomicInteger TASK1_TIMES = new AtomicInteger(0);
    public static final AtomicInteger TASK2_TIMES = new AtomicInteger(0);
    public static final AtomicInteger TASK3_TIMES = new AtomicInteger(0);
    public static final AtomicInteger TASK4_TIMES = new AtomicInteger(0);
    public static final AtomicInteger TASK5_TIMES = new AtomicInteger(0);

    @Inject
    RecoveryWorkflow recoveryWorkflow;

    @Inject
    RecoveryEmitWorkflow emitter;

    public static void reset() {
        TASK1_TIMES.set(0);
        TASK2_TIMES.set(0);
        TASK3_TIMES.set(0);
        TASK4_TIMES.set(0);
        TASK5_TIMES.set(0);
    }

    @GET
    @Path("/healthz")
    public String health() {
        return "ok";
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(new RecoveryResult(TASK1_TIMES.get(), TASK2_TIMES.get(), TASK3_TIMES.get(),
                TASK4_TIMES.get(), TASK5_TIMES.get())).build();
    }

    @POST
    @Path("/start")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @Blocking
    @ResponseStatus(202)
    public void start() {
        recoveryWorkflow.startInstance()
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
    public Uni<String> emit() {
        return emitter.startInstance("hello from recovery emitter")
                .onItem()
                .transformToUni(m -> Uni.createFrom().item(m.asText().orElseThrow()));
    }

    public record RecoveryResult(Integer task1, Integer task2, Integer task3, Integer task4, Integer task5) {
    }
}
