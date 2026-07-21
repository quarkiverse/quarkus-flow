package io.quarkiverse.flow.oidc.it;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowDSL;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

public abstract class DistinctEndpointImagesFlow extends Flow {

    @ConfigProperty(name = "auth.base-url")
    String baseUrl;

    @ConfigProperty(name = "endpoint.image.service.url")
    String imageService;

    protected abstract String workflowName();

    protected abstract String tokenPath();

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow(workflowName(), "quarkus-flow")
                .tasks(
                        FlowDSL.call(
                                FlowDSL.http("listImages")
                                        .get()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FlowDSL.oauth2(baseUrl, CLIENT_CREDENTIALS, "quarkus-flow",
                                                        "dummy-client-secret", e -> e.token(tokenPath())))))
                .build();
    }
}
