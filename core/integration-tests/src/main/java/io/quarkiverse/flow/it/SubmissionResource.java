package io.quarkiverse.flow.it;

import java.util.Map;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.Authenticated;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;

@Path("/submissions")
public class SubmissionResource {

    @Inject
    JsonWebToken jwt;

    final SubmissionWorkflow workflow;

    public SubmissionResource(SubmissionWorkflow workflow) {
        this.workflow = workflow;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    public Uni<Response> createSubmission(Map<String, Object> payload) {

        Map<String, Object> input = Map.of("token", jwt.getRawToken(), "payload", payload);
        return this.workflow.startInstance(input)
                .onItem()
                .transform(ignored -> Response.status(201).build());
    }

    @POST
    @Path("/token")
    @Produces(MediaType.TEXT_PLAIN)
    @PermitAll
    public Response token() {

        return Response.ok(
                Jwt.issuer("https://quarkus.io/issuer")
                        .upn("jdoe@quarkus.io")
                        .sign())
                .build();

    }
}
