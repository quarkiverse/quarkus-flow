package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow extends Flow {

    public Workflow descriptor() {
        return WorkflowBuilder.workflow("hello")
                .tasks(t -> t.set("sayHelloWorld", "{ message: \"hello world!\" }"))
                .build();
    }

}
