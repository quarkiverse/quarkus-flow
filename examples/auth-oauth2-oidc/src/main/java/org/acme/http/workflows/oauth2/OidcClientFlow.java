package org.acme.http.workflows.oauth2;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

@ApplicationScoped
public class OidcClientFlow extends Flow {

    @ConfigProperty(name = "image.service.url")
    String imageService;

    @ConfigProperty(name = "oidcClient.base-url")
    String baseUrl;

    public static final String OIDC_CLIENT_WORKFLOW_NAME = "oidc-client";

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow(
                OIDC_CLIENT_WORKFLOW_NAME, "quarkus-flow")
                .tasks(
                        FuncDSL.call(
                                FuncDSL.http("listImages")
                                        .GET()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FuncDSL.oauth2(baseUrl,
                                                        CLIENT_CREDENTIALS,
                                                        "${ $secret.oidcClient.\"client-id\" }",
                                                        "${ $secret.oidcClient.\"client-secret\" }",
                                                        e -> e.token("/protocol/openid-connect/token")))

                        ))
                .build();
    }
}
