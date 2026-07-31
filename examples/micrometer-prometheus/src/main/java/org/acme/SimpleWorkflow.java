package org.acme;

import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;
import static io.quarkiverse.flow.dsl.FlowDSL.set;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SimpleWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("simple-workflow", "quarkus-flow").tasks(set("{ message: \"Ana Sara\" }")).build();
    }
}
