package io.quarkiverse.flow.deployment.test.devui;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class DevUIWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return WorkflowBuilder.workflow("helloQuarkus")
                .tasks(t -> t.set("taskHello", """
                        { "message": "helloWorld" }
                        """))
                .build();
    }
}
