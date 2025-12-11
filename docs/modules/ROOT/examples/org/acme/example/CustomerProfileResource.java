package org.acme.example;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.smallrye.mutiny.Uni;

@Path("/api/profile")
@Produces(MediaType.APPLICATION_JSON)
public class CustomerProfileResource {

    @Inject
    CustomerProfileFlow customerProfileFlow;

    @GET
    public Uni<Map<String, Object>> getProfileViaWorkflow() {
        return customerProfileFlow.startInstance().onItem().transform(r -> r.asMap().orElseThrow());
    }
}
