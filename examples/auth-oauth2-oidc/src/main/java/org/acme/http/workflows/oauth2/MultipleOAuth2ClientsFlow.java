package org.acme.http.workflows.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient.ClientAuthentication;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MultipleOAuth2ClientsFlow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wireMock;

    @ConfigProperty(name = "joogle.baseUrl")
    String joogleBaseUrl;

    @ConfigProperty(name = "jahoo.baseUrl")
    String jahooBaseUrl;
    @Inject
    ObjectMapper objectMapper;

    @Override
    public Workflow descriptor() {

        return FuncWorkflowBuilder.workflow(
                "multiple-oauth2-clients", "quarkus-flow")
                .use(use -> {
                    use.secrets("joogle", "jahoo")
                            .authentications(auth -> {
                                auth.authentication("joogle", a -> {
                                    a.oauth2(oauth2 -> {
                                        oauth2.endpoints(e -> e.token("/auth/joogle/token"))
                                                .client(client -> client.id("${ $secret.joogle.clientId }")
                                                        .secret("${ $secret.joogle.clientSecret }")
                                                        .authentication(
                                                                ClientAuthentication.CLIENT_SECRET_POST))
                                                .authority(joogleBaseUrl)
                                                .grant(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
                                    });
                                });
                                auth.authentication("jahoo", a -> {
                                    a.oauth2(oauth2 -> {
                                        oauth2.endpoints(e -> e.token("/auth/jahoo/oidc/token"))
                                                .client(client -> client.id("${ $secret.jahoo.clientId }")
                                                        .secret("${ $secret.jahoo.clientSecret }")
                                                        .authentication(
                                                                ClientAuthentication.CLIENT_SECRET_POST))
                                                .authority(jahooBaseUrl)
                                                .grant(OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS);
                                    });
                                });
                            });

                })
                .tasks(
                        FuncDSL.fork(
                                FuncDSL.http("getEmailsFromJoogle").GET()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(wireMock + "/joogle/inbox"), FuncDSL.use("joogle")),
                                FuncDSL.http("getEmailsFromJahoo").GET()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(wireMock + "/jahoo/inbox"), FuncDSL.use("jahoo"))),
                        FuncDSL.function("merge", o -> o))
                .build();
    }
}
