package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ParallelWorkflowBranch extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("parallel-workflow-using-branch")
                .tasks(
                        funcDoTaskBuilder -> funcDoTaskBuilder.fork("checkCreditAndInventoryFork",
                                funcForkTaskBuilder -> funcForkTaskBuilder
                                        .branch(
                                                post("checkInventory", "", "http://localhost:8089/inventory-check"))
                                        .branch(
                                                call("checkCredit",
                                                        post("checkCredit", "", "http://localhost:8089/credit-check")))))
                .build();
    }
}
