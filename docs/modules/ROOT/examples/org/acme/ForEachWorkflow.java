package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import java.util.List;
import java.util.Map;

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
                        // OrdersPayload::orders is not working
                        // See https://github.com/quarkiverse/quarkus-flow/issues/486
                        forEach((Map<String, Object> state) -> (List<?>) state.get("orders"),
                                tasks(
                                        post("", "http://localhost:8089/process-order"))))
                .build();
    }
}
