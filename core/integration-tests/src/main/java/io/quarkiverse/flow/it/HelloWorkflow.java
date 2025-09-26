package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.FlowDescriptor;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow {

    @FlowDescriptor
    public Workflow helloWorld() {
        return WorkflowBuilder.workflow("hello")
                .tasks(t -> t.set("{ message: \"hello world!\" }"))
                .build();
    }

}
