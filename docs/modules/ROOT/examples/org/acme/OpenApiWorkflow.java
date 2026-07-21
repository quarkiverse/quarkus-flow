package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

// Static imports recommended for brevity:
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class OpenApiWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("openapi-call-workflow")
                .tasks(
                        openapi()
                                .document(wiremockUrl + "/v2/swagger.json")
                                .operation("findPetsByStatus")
                                .parameters(Map.of("status", "available")))
                .build();
    }
}
