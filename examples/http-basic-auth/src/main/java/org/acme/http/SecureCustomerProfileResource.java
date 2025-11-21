package org.acme.http;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.Context;

import java.time.Instant;
import java.util.Map;

@Path("/secure/profile")
@Produces(MediaType.APPLICATION_JSON)
public class SecureCustomerProfileResource {

    @Context
    SecurityContext securityContext;

    @GET
    @RolesAllowed("user")
    public Map<String, Object> getProfile() {
        // In a real system, you'd look up the current customer context.
        // Here we just return a canned "profile" plus the caller principal.
        String caller = securityContext.getUserPrincipal() != null
                ? securityContext.getUserPrincipal().getName()
                : "anonymous";

        return Map.of(
                "customerId", 123,
                "name", "Jane Doe",
                "tier", "GOLD",
                "lastUpdatedAt", Instant.now().toString(),
                "servedBy", caller
        );
    }
}
