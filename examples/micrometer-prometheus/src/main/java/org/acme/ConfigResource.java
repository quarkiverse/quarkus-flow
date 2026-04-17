package org.acme;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;

@Path("/config")
public class ConfigResource {

    @ConfigProperty(name = "grafana.endpoint")
    Optional<String> grafanaEndpoint;

    @GET
    @Path("/endpoints")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getEndpoints() {
        return Map.of(
                "grafana", grafanaEndpoint.orElse("http://localhost:3000"),
                "prometheus", "/q/metrics");
    }
}
