package io.quarkiverse.flow.durable.kube.it;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class GreetingsFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("greetings", "io.quarkiverse.flow.durable.kube")
                .tasks(set(Map.of("message", "Hello Human!")))
                .build();
    }
}
