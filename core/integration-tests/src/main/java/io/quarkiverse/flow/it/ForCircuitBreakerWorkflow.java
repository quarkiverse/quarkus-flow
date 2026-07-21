package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.dsl.FlowDSL.openapi;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class ForCircuitBreakerWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        final URI problematic = URI.create("openapi/problematic.json");

        return FlowWorkflowBuilder.workflow("for-cb-workflow")
                .tasks(openapi("findNothing").document(problematic).operation("getProblematic")
                        .outputAs("${{ message }}"))
                .build();
    }
}
