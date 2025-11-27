package io.quarkiverse.flow.deployment.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.flow.Flow;
import io.quarkus.arc.Arc;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

@Path("/identifier")
public class IdentifierResource {

    @GET
    @Path("/workflow-def")
    @Produces(MediaType.TEXT_PLAIN)
    public Response workflowDef(@QueryParam("identifier") String identifier) {
        var handle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of(identifier));
        if (!handle.isAvailable()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var document = handle.get().workflow().getDocument();
        return Response.ok(document.getNamespace() + ":" + document.getName()).build();
    }

    @GET
    @Path("/flow")
    @Produces(MediaType.TEXT_PLAIN)
    public Response flow(@QueryParam("identifier") String identifier) {
        var handle = Arc.container().instance(Flow.class,
                Identifier.Literal.of(identifier));
        if (!handle.isAvailable()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String value = handle.getBean()
                .getBeanClass().getAnnotation(Identifier.class)
                .value();

        return Response.ok(value).build();
    }
}
