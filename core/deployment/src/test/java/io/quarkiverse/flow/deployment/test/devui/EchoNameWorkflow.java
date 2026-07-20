package io.quarkiverse.flow.deployment.test.devui;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class EchoNameWorkflow extends Flow {
    public Workflow descriptor() {
        return WorkflowBuilder
                .workflow("echo-name", "flow", "0.1.0")
                .tasks(t -> t.set("setEcho", "${ \"Echo from test: \" + .name }"))
                .build();
    }
}
