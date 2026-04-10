package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.oauth2;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HttpOauth2Workflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("oauth2-authentication-workflow")
                .tasks(
                        call("getPets",
                                http()
                                        .GET()
                                        // Hardcoded 99 should be {petId}, but this can't be
                                        // resolved properly by the engine
                                        .uri("http://localhost:8090/v2/pet/99",
                                                oauth2("http://localhost:8090/realms/fake-authority",
                                                        OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
                                                        "workflow-runtime-id",
                                                        "workflow-runtime-secret"))

                        ))
                .build();
    }
}
