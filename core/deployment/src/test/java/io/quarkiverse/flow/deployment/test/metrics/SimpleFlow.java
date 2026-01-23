package io.quarkiverse.flow.deployment.test.metrics;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SimpleFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("named")
                .tasks(
                        set("{ \"message\": .message }"))
                .build();
    }
}
