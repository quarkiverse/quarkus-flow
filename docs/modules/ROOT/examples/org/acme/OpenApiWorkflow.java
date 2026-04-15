package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

// Static imports recommended for brevity:
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class OpenApiWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("openapi-call-workflow")
                .tasks(
                        openapi()
                                // "http://localhost:8089/v2/swagger.json" can't be parsed -> JsonQueryException
                                .document("${ \"http://localhost:8089/v2/swagger.json\" }")
                                .operation("findPetsByStatus")
                                .parameters(Map.of("status", "available"))
                // Wrap the JQ object constructor in ${ } so the engine evaluates it correctly
                // Just {pets: .} does not behave as expected
                // .exportAs("${ {pets: .} }") - also does not help
                )
                .build();
    }
}
