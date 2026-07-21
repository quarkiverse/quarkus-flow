package io.quarkiverse.flow.persistence.jpa.test;

import static io.quarkiverse.flow.dsl.FlowDSL.set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class HelloWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("hello")
                .tasks(set("{ message: \"hello world!\" }"))
                .build();

    }

}
