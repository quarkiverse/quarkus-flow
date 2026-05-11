package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ParallelWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("parallel-workflow-using-branches")
                .tasks(
                        fork("checkInventoryAndCredit",
                                http("checkInventory")
                                        .method("POST")
                                        .body("")
                                        .endpoint("http://localhost:8089/inventory-check"),
                                http("checkCredit")
                                        .method("POST")
                                        .body("")
                                        .endpoint("http://localhost:8089/credit-check")))
                .build();
    }
}
