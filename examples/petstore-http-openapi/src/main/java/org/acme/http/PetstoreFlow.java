package org.acme.http;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;

@ApplicationScoped
public class PetstoreFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder
                .workflow("petstore")
                .tasks(get("https://petstore.swagger.io/v2/pet/findByStatus?status=sold")
                                .exportAs("${ { selectedPetId: .[0].id } }"),
                        get("https://petstore.swagger.io/v2/pet/{selectedPetId}")
                ).build();
    }
}
