package io.quarkiverse.flow.oidc.it;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowDSL;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Base for flows that call an OAuth2 (Client Credentials) protected HTTP service requesting a specific {@code scope}. Two
 * concrete subclasses share the same authority/client/grant but differ only by scope: they must negotiate distinct tokens
 * (one {@code OidcClient} each), so that each token request actually carries the requested scope.
 */
public abstract class ScopedImagesFlow extends Flow {

    @ConfigProperty(name = "auth.base-url")
    String baseUrl;

    @ConfigProperty(name = "scoped.image.service.url")
    String imageService;

    protected abstract String workflowName();

    protected abstract String scope();

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow(workflowName(), "quarkus-flow")
                .tasks(
                        FlowDSL.call(
                                FlowDSL.http("listImages")
                                        .get()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FlowDSL.oauth2(oauth2 -> oauth2
                                                        .authority(baseUrl)
                                                        .client(c -> c.id("quarkus-flow").secret("dummy-client-secret"))
                                                        .grant(CLIENT_CREDENTIALS)
                                                        .scopes(scope())))))
                .build();
    }
}
