package io.quarkiverse.flow.it;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowDSL;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Calls an OAuth2 (client-credentials) protected HTTP service using the Open Workflow SDK's default token
 * negotiation (no {@code quarkus-flow-oidc} extension on the classpath).
 */
@ApplicationScoped
public class OAuth2ClientCredentialsWorkflow extends Flow {

    @ConfigProperty(name = "oauth2.it.authority")
    String authority;

    @ConfigProperty(name = "oauth2.it.protected-resource-url")
    String protectedResourceUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("oauth2-client-credentials-it", "quarkus-flow")
                .tasks(
                        FlowDSL.call(
                                FlowDSL.http("call")
                                        .get()
                                        .uri(URI.create(protectedResourceUrl),
                                                FlowDSL.oauth2(authority, CLIENT_CREDENTIALS, "quarkus-flow",
                                                        "dummy-client-secret", e -> e.token("/oauth2/token")))))
                .build();
    }
}
