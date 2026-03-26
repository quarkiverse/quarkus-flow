package io.quarkiverse.flow.it;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.openapi;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ForCircuitBreakerWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        final URI problematic = URI.create("openapi/problematic-local.json");

        return FuncWorkflowBuilder.workflow("for-cb-workflow")
                .tasks(openapi("findNothing").document(problematic).operation("getProblematic")
                        .outputAs("${{ message }}"))
                .build();
    }
}
