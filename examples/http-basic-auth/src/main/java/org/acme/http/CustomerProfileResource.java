package org.acme.http;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/profile")
@Produces(MediaType.APPLICATION_JSON)
public class CustomerProfileResource {

    @Inject
    CustomerProfileFlow customerProfileFlow;

    @GET
    public CompletionStage<Map<String, Object>> getProfileViaWorkflow() throws Exception {
        return customerProfileFlow.instance(Map.of())
                .start()
                .thenApply(r -> r.asMap().orElseThrow());
    }
}
