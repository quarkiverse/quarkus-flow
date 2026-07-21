package io.quarkiverse.flow.oidc.it;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowDSL;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class NamedClientFlow extends Flow {

    public static final String NAME = "named-client-it";
    public static final String NAMESPACE = "quarkus-flow";

    @ConfigProperty(name = "named.image.service.url")
    String imageService;

    @ConfigProperty(name = "auth.base-url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow(NAME, NAMESPACE)
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", r -> r.oauth2(
                        oauth2 -> oauth2.authority(baseUrl)
                                .grant(CLIENT_CREDENTIALS)))))
                .tasks(
                        FlowDSL.call(
                                FlowDSL.http("listNamedImages")
                                        .get()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService), FlowDSL.use("keycloak"))))
                .build();
    }
}
