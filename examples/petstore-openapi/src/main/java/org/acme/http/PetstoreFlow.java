package org.acme.http;

import java.net.URI;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.openapi;

@ApplicationScoped
public class PetstoreFlow extends Flow {

    @Override
    public Workflow descriptor() {
        final URI petstoreUri = URI.create("openapi/petstore.json");

        return FuncWorkflowBuilder
                .workflow("petstore")
                // You find the operation in the spec file, field operationId.
                .tasks(openapi("findPetByStatus").document(petstoreUri).operation("findPetsByStatus").parameter("status", "sold")
                        // We use a jq expression to select from the JSON array the first item after the task response.
                        .outputAs("${ { selectedPetId: .[0].id } }"),
                        openapi("getPetById").document(petstoreUri).operation("getPetById")
                                // We set the parameter from the data context, again via a JQ expression
                                .parameter("petId", "${.selectedPetId}"))
                .build();
    }

}
