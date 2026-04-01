package org.acme.flow.durable.kube;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/delay")
@Produces(MediaType.APPLICATION_JSON)
public class DelayedServiceResource {

    @GET
    @Path("/operation")
    @Blocking
    public Response delayedOperation() throws InterruptedException {
        Thread.sleep(5000);
        return Response.ok("{ \"response\": \"OK\" }").build();
    }

}
