package org.acme.http;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

@Path("/")
@ApplicationScoped
public class PetstoreResource {

    @Inject
    PetstoreFlow petstoreFlow;

    @Inject
    Template index;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance page() {
        return index.data("docsUrl", "https://docs.quarkiverse.io/quarkus-flow/dev/");
    }

    @GET
    @Path("/pet")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Map<String, Object>> getPet() throws Exception {
        return petstoreFlow.instance(Map.of())
                .start()
                .thenApply(result -> result.asMap().orElseThrow());
    }
}
