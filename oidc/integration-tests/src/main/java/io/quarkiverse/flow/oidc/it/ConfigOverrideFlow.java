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
 * A workflow whose OIDC auth-server-url is overridden from {@code application.properties}. The DSL declares
 * {@code auth.base-url} as the authority, but the config override
 * {@code quarkus.flow.oidc.client."quarkus-flow:config-override-it".auth-server-url} redirects the token request to a
 * different WireMock endpoint.
 */
@ApplicationScoped
public class ConfigOverrideFlow extends Flow {

    public static final String NAME = "config-override-it";
    public static final String NAMESPACE = "quarkus-flow";

    @ConfigProperty(name = "config.override.image.service.url")
    String imageService;

    @ConfigProperty(name = "auth.base-url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow(NAME, NAMESPACE)
                .tasks(
                        FlowDSL.call(
                                FlowDSL.http("listOverriddenImages")
                                        .get()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FlowDSL.oauth2(baseUrl, CLIENT_CREDENTIALS, "quarkus-flow",
                                                        "dummy-client-secret",
                                                        e -> e.token("/oauth2/token")))))
                .build();
    }
}
