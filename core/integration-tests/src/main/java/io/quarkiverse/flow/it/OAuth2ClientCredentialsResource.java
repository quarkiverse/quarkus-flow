package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import io.smallrye.mutiny.Uni;

@Path("/oauth2-it")
@ApplicationScoped
public class OAuth2ClientCredentialsResource {

    @Inject
    OAuth2ClientCredentialsWorkflow workflow;

    @GET
    public Uni<Response> call() {
        return workflow.startInstance()
                .onItem()
                .transform(model -> Response.ok(model.asJavaObject()).build());
    }
}
