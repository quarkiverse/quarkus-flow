package io.quarkiverse.flow.oidc.it;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowDSL;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Calls an OAuth2 (Client Credentials) protected HTTP service. Quarkus Flow negotiates the token through
 * {@code quarkus-oidc-client} and attaches {@code Authorization: Bearer <token>} to the downstream call.
 */
@ApplicationScoped
public class ClientCredentialsFlow extends Flow {

    public static final String NAME = "client-credentials-it";

    @ConfigProperty(name = "image.service.url")
    String imageService;

    @ConfigProperty(name = "auth.base-url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow(NAME, "quarkus-flow")
                .tasks(
                        FlowDSL.call(
                                FlowDSL.http("listImages")
                                        .get()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FlowDSL.oauth2(baseUrl, CLIENT_CREDENTIALS, "quarkus-flow",
                                                        "dummy-client-secret", e -> e.token("/oauth2/token")))))
                .build();
    }
}
