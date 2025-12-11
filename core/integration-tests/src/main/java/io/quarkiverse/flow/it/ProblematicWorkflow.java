package io.quarkiverse.flow.it;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.openapi;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ProblematicWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        final URI problematic = URI.create("openapi/problematic.json");

        return FuncWorkflowBuilder.workflow("problematic-workflow")
                // You find the operation in the spec file, field operationId.
                .tasks(openapi("findNothing").document(problematic).operation("getProblematic")
                        // We use a jq expression to select from the JSON array the first item after the task response.
                        .outputAs("${{ message }}")) // the code will fail before for testing ExceptionMapper
                .build();
    }

}
