package io.quarkiverse.flow.runner.it;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SimpleGreetingFlowV1_5 extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("simple-greeting", "test-namespace", "1.5.0")
                .tasks(
                        set(Map.of("greeting", "${.name + \" says hello from version v1.5.0!\"}")))
                .build();
    }
}
