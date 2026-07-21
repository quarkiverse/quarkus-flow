package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.dsl.FlowDSL.openapi;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class ProblematicWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        final URI problematic = URI.create("openapi/problematic.json");

        return FlowWorkflowBuilder.workflow("problematic-workflow")
                // You find the operation in the spec file, field operationId.
                .tasks(openapi("findNothing").document(problematic).operation("getProblematic")
                        // We use a jq expression to select from the JSON array the first item after the task response.
                        .outputAs("${{ message }}")) // the code will fail before for testing ExceptionMapper
                .build();
    }

}
