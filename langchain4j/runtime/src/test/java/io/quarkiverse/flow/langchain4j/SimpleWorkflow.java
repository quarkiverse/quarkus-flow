package io.quarkiverse.flow.langchain4j;

import static io.quarkiverse.flow.dsl.FlowDSL.set;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SimpleWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("simple-workflow", "quarkus.flow").tasks(set("{ message: \"Ana Sara\" }")).build();
    }
}
