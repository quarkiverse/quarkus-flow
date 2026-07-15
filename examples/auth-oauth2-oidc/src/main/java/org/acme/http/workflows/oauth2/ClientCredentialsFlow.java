package org.acme.http.workflows.oauth2;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

import static io.serverlessworkflow.api.types.OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS;

@ApplicationScoped
public class ClientCredentialsFlow extends Flow {

    @ConfigProperty(name = "image.service.url")
    String imageService;

    @ConfigProperty(name = "clientCredentials.baseUrl")
    String baseUrl;

    @Override
    public Workflow descriptor() {

        return FuncWorkflowBuilder.workflow(
                "client-credentials", "quarkus-flow")
                .use(u -> u.secrets("clientCredentials"))
                .tasks(
                        FuncDSL.call(
                                FuncDSL.http("listImages")
                                        .GET()
                                        .header("Accept", "application/json")
                                        .uri(URI.create(imageService),
                                                FuncDSL.oauth2(baseUrl,
                                                        CLIENT_CREDENTIALS,
                                                        "${ $secret.clientCredentials.clientId }",
                                                        "${ $secret.clientCredentials.clientSecret }",
                                                        e -> e.token("/protocol/openid-connect/token")))

                        ))
                .build();
    }
}
