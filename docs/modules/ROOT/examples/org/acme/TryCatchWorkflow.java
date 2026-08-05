package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class TryCatchWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("try-catch-workflow")
                .tasks(
                        tryCatch("safeFetch", t -> t
                                .tryCatch(
                                        post("fetchPet", "", wiremockUrl + "/v2/pet/1"))
                                .catchError(
                                        err -> err.type(
                                                "https://serverlessworkflow.io/spec/1.0.0/errors/communication"),
                                        set("{ \"found\": false }"))))
                .build();
    }
}
