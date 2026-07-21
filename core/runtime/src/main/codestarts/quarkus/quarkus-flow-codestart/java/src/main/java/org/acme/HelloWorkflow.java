package org.acme;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;

import static io.quarkiverse.flow.dsl.FlowDSL.set;

@ApplicationScoped
public class HelloWorkflow extends Flow {

    @Override
    public Workflow descriptor () {

        return FlowWorkflowBuilder.workflow("hello")
                // jq expression to set our context to the JSON object `message`
                .tasks(set("{ message: \"hello world!\" }"))
                .build();

    }

}
