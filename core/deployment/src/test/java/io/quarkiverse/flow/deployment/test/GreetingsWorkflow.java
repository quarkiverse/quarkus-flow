package io.quarkiverse.flow.deployment.test;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import io.smallrye.common.annotation.Identifier;

@ApplicationScoped
@Identifier("greetings")
public class GreetingsWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return WorkflowBuilder.workflow()
                .tasks(t -> t.set(s -> s.expr("{ message: \"Saludos \" + .name }").when(".language == \"spanish\""))
                        .set(s -> s.expr("{ message: \"Salve \" + .name }").when(".language == \"portuguese\""))
                        .set(s -> s.expr("{ message: \"Howdy \" + .name }").when(".language == \"english\"")))
                .build();
    }

}
