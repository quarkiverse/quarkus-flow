package org.acme.http.workflows.oauth2;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.dsl.FlowDSL;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;

@ApplicationScoped
public class TokenExchangeGrantTypeFlow extends Flow {

    @ConfigProperty(name = "image.service.url")
    String imageService;

    @ConfigProperty(name = "myRealm.base-url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow(
                "token-exchange", "quarkus-flow")
                .use(use -> use.secrets("exchangeSecrets"))
                .tasks(
                        FlowDSL.http()
                                .method("DELETE")
                                .uri(URI.create(imageService + "/attrs/dcb507bd-4dc4-46ba-a4ae-eb622b817d62"),

                                        FlowDSL.oauth2(oauth2 -> {
                                            oauth2
                                                    .endpoints(token -> token.token("/oauth2/token"))
                                                    .client(client -> {
                                                        client.id("my-client")
                                                                .secret("my-secret");
                                                    }).subject(subject -> {
                                                        subject.token("${ $secret.exchangeSecrets.subjectToken }")
                                                                .type("urn:ietf:params:oauth:token-type:access_token");
                                                    }).actor(actor -> actor
                                                            .token("${ $secret.exchangeSecrets.actorToken }")
                                                            .type("urn:ietf:params:oauth:token-type:access_token"))
                                                    .scopes("api")
                                                    .audiences("target-service")
                                                    .grant(OAuth2AuthenticationDataGrant.URN_IETF_PARAMS_OAUTH_GRANT_TYPE_TOKEN_EXCHANGE)
                                                    .authority(baseUrl);
                                        })))
                .build();

    }
}
