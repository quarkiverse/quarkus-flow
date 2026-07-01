package org.acme.http.workflows.oauth2;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

@ApplicationScoped
public class PasswordGrantTypeFlow extends Flow {

    @ConfigProperty(name = "image.service.url")
    String imageService;

    @ConfigProperty(name = "password-grant.base-url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow(
                "grant-type-password", "quarkus.flow")
                .use(u -> u.secrets("password-grant"))
                .tasks(
                        FuncDSL.http()
                                .method("DELETE")
                                .uri(URI.create(imageService + "/attrs/dcb507bd-4dc4-46ba-a4ae-eb622b817d62"),
                                        FuncDSL.oauth2(oauth2 -> {
                                            oauth2.authority(baseUrl)
                                                    .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.PASSWORD)
                                                    .client(client -> client.id("${ $secret.\"password-grant\".\"client-id\" }")
                                                            .secret("${ $secret.\"password-grant\".\"client-secret\" }"))
                                                    .username("${ $secret.\"password-grant\".username }")
                                                    .password("${ $secret.\"password-grant\".password }");

                                        })))

                .build();
    }
}
