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

@Path("/durable/recovery-wait")
public class RecoveryWaitResource {

    public static final AtomicInteger TASK1_TIMES = new AtomicInteger(0);
    public static final AtomicInteger TASK2_TIMES = new AtomicInteger(0);
    public static final AtomicInteger TASK3_WAIT_TASK = new AtomicInteger(0);
    public static final AtomicInteger TASK4_TIMES = new AtomicInteger(0);

    @Inject
    RecoveryWaitWorkflow recoveryWaitWorkflow;

    public static void reset() {
        TASK1_TIMES.set(0);
        TASK2_TIMES.set(0);
        TASK3_WAIT_TASK.set(0);
        TASK4_TIMES.set(0);
    }

    @GET
    @Path("/healthz")
    public String health() {
        return "ok";
    }

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(new RecoveryWaitResult(TASK1_TIMES.get(), TASK2_TIMES.get(), TASK3_WAIT_TASK.get(),
                TASK4_TIMES.get())).build();
    }

    @POST
    @Path("/start")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @Blocking
    @ResponseStatus(202)
    public void start() {
        recoveryWaitWorkflow.startInstance()
                .subscribe()
                .with(ignore -> {
                });
    }

    public record RecoveryWaitResult(Integer task1, Integer task2, Integer task3, Integer task4) {
    }
}
