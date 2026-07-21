package org.acme.http.workflows.oauth2;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.Workflow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.dsl.FlowDSL;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@ApplicationScoped
public class OpenAPIWithOAuth2Flow extends Flow {

    @ConfigProperty(name = "quarkus.wiremock.devservices.port")
    String wireMockPort;

    @ConfigProperty(name = "openapi.base-url")
    String baseUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow()
                .use(u -> u.secrets("openapi"))
                .tasks(t -> {
                    t.openapi("imageService", f ->
                    // WireMock replaces the {{wireMockPort}} using response-template transformations
                    f.document("http://localhost:" + wireMockPort + "/openapi/openapi-oauth2.yaml?wireMockPort=" + wireMockPort)
                            .operation("listImages")
                            .parameters(Map.of(
                                    "Accept", "application/json"))
                            .authentication(FlowDSL.oauth2(
                                    baseUrl,
                                    OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                    "${ $secret.openapi.\"client-id\" }", "${ $secret.openapi.\"client-secret\" }",
                                    e -> e.token("/protocol/openid-connect/token"))));
                })
                .build();
    }
}
