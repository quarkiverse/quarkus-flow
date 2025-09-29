package io.quarkiverse.flow.deployment.test;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloWorldWorkflow extends Flow {
    public Workflow descriptor() {
        return WorkflowBuilder
                .workflow()
                .tasks(t -> t.set("${ .message }"))
                .build();
    }
}
