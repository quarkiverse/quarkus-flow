package io.quarkiverse.flow.durable.kube.it;

import java.util.Map;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

public class GreetingsFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("greetings", "io.quarkiverse.flow.durable.kube")
                .tasks(FuncDSL.set(Map.of("message", "Hello Human!")))
                .build();
    }
}
