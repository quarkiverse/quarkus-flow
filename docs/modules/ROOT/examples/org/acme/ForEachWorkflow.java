package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ForEachWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("foreach-workflow")
                .tasks(
                        forEach(OrdersPayload::orders,
                                tasks(
                                        post("$item.id",
                                                "http://localhost:8089/process-order")
                                                .exportAsTaskOutput())))
                .build();
    }
}
