package org.acme.codestart;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

@ApplicationScoped
public class HelloWorkflow extends Flow {

    @Override
    public Workflow descriptor () {

        return FuncWorkflowBuilder.workflow("hello")
                // jq expression to set our context to the JSON object `message`
                .tasks(set("{ message: \"hello world!\" }"))
                .build();

    }

}
