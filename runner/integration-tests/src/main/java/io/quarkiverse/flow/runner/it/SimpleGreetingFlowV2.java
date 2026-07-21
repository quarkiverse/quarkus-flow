package io.quarkiverse.flow.runner.it;

import static io.quarkiverse.flow.dsl.FlowDSL.set;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SimpleGreetingFlowV2 extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("simple-greeting", "test-namespace", "2.0.0")
                .tasks(
                        set(Map.of("greeting", "${.name + \" says hello from version v2.0.0!\"}")))
                .build();
    }
}
