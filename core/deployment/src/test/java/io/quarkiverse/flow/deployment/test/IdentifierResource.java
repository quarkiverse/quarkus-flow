package io.quarkiverse.flow.deployment.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.arc.Arc;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

@Path("/identifier")
public class IdentifierResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response simple(@QueryParam("identifier") String identifier) {
        var handle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of(identifier));
        if (!handle.isAvailable()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var document = handle.get().workflow().getDocument();
        return Response.ok(document.getNamespace() + ":" + document.getName()).build();
    }
}
