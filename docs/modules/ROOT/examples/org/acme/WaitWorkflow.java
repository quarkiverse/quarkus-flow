package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.waitSeconds;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class WaitWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("wait-workflow")
                .tasks(
                        waitSeconds("pause", 10))
                .build();
    }
}
