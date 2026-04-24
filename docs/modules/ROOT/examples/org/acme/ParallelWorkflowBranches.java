package org.acme;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ParallelWorkflowBranches extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("parallel-workflow-using-branches")
                .tasks(
                        funcTaskItemListBuilder -> funcTaskItemListBuilder.fork(
                                funcForkTaskBuilder -> funcForkTaskBuilder.branches(
                                        inner -> {
                                            inner.http("checkInventory",
                                                    h -> h.method("POST").body("")
                                                            .endpoint("http://localhost:8089/inventory-check"));
                                            inner.http("checkCredit",
                                                    h -> h.method("POST").body("")
                                                            .endpoint("http://localhost:8089/credit-check"));
                                        })

                        ))
                .build();
    }
}