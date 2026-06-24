package org.acme.grpc;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.serverlessworkflow.impl.WorkflowModel;

@Path("/grpc-greeting")
@Produces(MediaType.TEXT_PLAIN)
public class GrpcGreetingResource {

    @Inject
    GrpcGreetingFlow flow;

    @GET
    public String greet(@QueryParam("name") String name) {
        WorkflowModel result = flow.startInstance(Map.of("name", name)).await().indefinitely();
        return result.asMap()
                .map(m -> (String) m.get("message"))
                .or(() -> result.asText())
                .orElse(null);
    }
}
