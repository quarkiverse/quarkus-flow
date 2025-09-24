package io.quarkiverse.flow.deployment.test;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.FlowDescriptor;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class ExampleWorkflows {

    @FlowDescriptor
    public Workflow helloWorld() {
        return WorkflowBuilder
                .workflow()
                .tasks(t -> t.set("${ .message }"))
                .build();
    }

    @FlowDescriptor
    public Workflow greetings() {
        return WorkflowBuilder.workflow()
                .tasks(t -> t.set(s -> s.expr("{ message: \"Saludos \" + .name }").when(".language == \"spanish\""))
                        .set(s -> s.expr("{ message: \"Salve \" + .name }").when(".language == \"portuguese\""))
                        .set(s -> s.expr("{ message: \"Howdy \" + .name }").when(".language == \"english\"")))
                .build();
    }

}
