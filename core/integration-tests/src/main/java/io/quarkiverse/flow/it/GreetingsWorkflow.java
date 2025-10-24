package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class GreetingsWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return WorkflowBuilder.workflow("Greetings i18n")
                .tasks(t -> t
                        .set("define spanish",
                                s -> s.expr("{ message: \"Saludos \" + .name }").when(".language == \"spanish\""))
                        .set("define portuguese",
                                s -> s.expr("{ message: \"Salve \" + .name }").when(".language == \"portuguese\""))
                        .set("define english",
                                s -> s.expr("{ message: \"Howdy \" + .name }").when(".language == \"english\"")))
                .build();
    }

}
