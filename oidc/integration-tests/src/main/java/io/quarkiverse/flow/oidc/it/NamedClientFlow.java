package io.quarkiverse.flow.oidc.it;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

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
        return FuncWorkflowBuilder.workflow(NAME, NAMESPACE)
                .use(use -> use.authentications(auth -> auth.authentication("keycloak", r -> r.oauth2(
                        oauth2 -> oauth2.authority(baseUrl)
                                .grant(CLIENT_CREDENTIALS)))))
                .tasks(
                        FuncDSL.call(
                                FuncDSL.http("listNamedImages")
                                        .GET()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService), FuncDSL.use("keycloak"))))
                .build();
    }
}
