package io.quarkiverse.flow.langchain4j;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

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
