package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.oauth2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HttpOauth2Workflow extends Flow {

    @Inject
    @ConfigProperty(name = "wiremock.secure.url")
    String wiremockSecureUrl;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("oauth2-authentication-workflow")
                .tasks(
                        call("getPets",
                                http()
                                        .GET()
                                        .query("petId", "${ .petId }")
                                        .uri(wiremockSecureUrl + "/v2/pet",
                                                oauth2(wiremockSecureUrl + "/realms/fake-authority",
                                                        OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                                        "workflow-runtime-id",
                                                        "workflow-runtime-secret"))

                        ))
                .build();
    }
}
