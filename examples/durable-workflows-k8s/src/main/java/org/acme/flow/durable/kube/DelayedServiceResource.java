package org.acme.flow.durable.kube;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/delay")
@Produces(MediaType.APPLICATION_JSON)
public class DelayedServiceResource {

    @ConfigProperty(defaultValue = "1000", name = "org.acme.flow.durable.kube.sleep-seconds")
    int sleepSeconds;

    @GET
    @Path("/operation")
    @Blocking
    public Response delayedOperation() throws InterruptedException {
        Thread.sleep(sleepSeconds * 1000L);
        return Response.ok("{ \"response\": \"OK\" }").build();
    }

}
