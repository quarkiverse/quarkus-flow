package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;
import static io.quarkiverse.flow.dsl.FlowDSL.oauth2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class HttpOauth2Workflow extends Flow {

    @Inject
    @ConfigProperty(name = "wiremock.secure.url")
    String wiremockSecureUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("oauth2-authentication-workflow")
                .tasks(
                        call("getPets",
                                http()
                                        .get()
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
