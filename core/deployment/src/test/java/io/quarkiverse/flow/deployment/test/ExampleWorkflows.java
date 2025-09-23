package io.quarkiverse.flow.deployment.test;

import io.quarkiverse.flow.FlowDefinition;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExampleWorkflows {

    @FlowDefinition
    public Workflow helloWorld() {
        return WorkflowBuilder
                .workflow("hello-world")
                .tasks(t -> t.set("${ .message }"))
                .build();
    }

    @FlowDefinition
    public Workflow greetings() {
        return WorkflowBuilder.workflow("greetings")
                .tasks(t ->
                        t.when("${ .language == 'spanish' }").set("${ { message = \"Saludos \" + .name } }")
                                .when("${ language == 'portuguese' }").set("${ { message = \"Salve \" + .name } }")
                                .when("${ language == 'english'}").set("${ { message = \"Howdy \" + .name } }"))
                .build();
    }

}
