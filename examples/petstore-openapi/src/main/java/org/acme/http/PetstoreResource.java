package org.acme.http;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

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
    public Uni<Map<String, Object>> getPet() throws Exception {
        return petstoreFlow.startInstance().onItem().transform(result -> result.asMap().orElseThrow());
    }
}
