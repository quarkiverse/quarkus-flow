package io.quarkiverse.flow.persistence.redis.deployment.durable;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;

@Path("/durable")
public class DurableResource {

    @Inject
    ListenWorkflow listenWorkflow;

    @Inject
    EmitWorkflow emitter;

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
        return emitter.startInstance()
                .onItem()
                .transformToUni(m -> Uni.createFrom().item(m.asText().orElseThrow()));
    }
}
