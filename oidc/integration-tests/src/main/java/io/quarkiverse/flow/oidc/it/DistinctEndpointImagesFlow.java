package io.quarkiverse.flow.oidc.it;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

public abstract class DistinctEndpointImagesFlow extends Flow {

    @ConfigProperty(name = "auth.base-url")
    String baseUrl;

    @ConfigProperty(name = "endpoint.image.service.url")
    String imageService;

    protected abstract String workflowName();

    protected abstract String tokenPath();

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow(workflowName(), "quarkus-flow")
                .tasks(
                        FuncDSL.call(
                                FuncDSL.http("listImages")
                                        .GET()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FuncDSL.oauth2(baseUrl, CLIENT_CREDENTIALS, "quarkus-flow",
                                                        "dummy-client-secret", e -> e.token(tokenPath())))))
                .build();
    }
}
