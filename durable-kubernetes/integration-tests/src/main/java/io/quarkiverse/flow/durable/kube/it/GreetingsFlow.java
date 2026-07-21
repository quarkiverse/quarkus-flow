package io.quarkiverse.flow.durable.kube.it;

import static io.quarkiverse.flow.dsl.FlowDSL.set;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class GreetingsFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("greetings", "io.quarkiverse.flow.durable.kube")
                .tasks(set(Map.of("message", "Hello Human!")))
                .build();
    }
}
