package io.quarkiverse.flow.it;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class EchoAgenticWorkflow extends Flow {

    @Inject
    EchoAgent echoAgent;

    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("workflowEchoAgentic")
                .tasks(function("interactWithAI", echoAgent::helloWorld, String.class))
                .build();
    }

}
