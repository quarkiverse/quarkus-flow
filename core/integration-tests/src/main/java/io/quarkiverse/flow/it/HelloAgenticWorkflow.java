package io.quarkiverse.flow.it;

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
                .tasks(t -> t.callFn("interactWithAI", f -> f.function(helloAgent::helloWorld)))
                .build();
    }

}
