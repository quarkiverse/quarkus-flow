package org.acme.example;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.openapi;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class PetstoreFlow extends Flow {

    @Override
    public Workflow descriptor() {
        final URI petstoreUri = URI.create("openapi/petstore.json");

        return workflow("petstore")
                .tasks(
                        // 1) Find pets by status
                        openapi()
                                .document(petstoreUri).operation("findPetsByStatus").parameter("status", "sold")
                                // Pick the first pet id and expose it as `selectedPetId`
                                .outputAs("${ { selectedPetId: .[0].id } }"),
                        // 2) Fetch that pet by id
                        openapi()
                                .document(petstoreUri).operation("getPetById").parameter("petId", "${ .selectedPetId }"))
                .build();
    }
}
