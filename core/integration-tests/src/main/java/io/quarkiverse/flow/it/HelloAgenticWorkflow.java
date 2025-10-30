package io.quarkiverse.flow.it;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HelloAgenticWorkflow extends Flow {

    @Inject
    HelloAgent helloAgent;

    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("helloAgent")
                .tasks(function("interactWithAI", helloAgent::helloWorld, String.class))
                .build();
    }

}
