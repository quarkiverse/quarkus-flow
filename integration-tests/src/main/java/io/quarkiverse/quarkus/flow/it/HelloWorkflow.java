package io.quarkiverse.quarkus.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.FlowDefinition;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow {

    @FlowDefinition
    public Workflow helloWorld() {
        return WorkflowBuilder.workflow("hello")
                .tasks(t -> t.set("{ message: \"hello world!\" }"))
                .build();
    }

}
