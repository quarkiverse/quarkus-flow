package org.acme;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SimpleWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("simple-workflow", "quarkus.flow")
                .tasks(set("{ message: \"Ana Sara\" }")).build();
    }
}
