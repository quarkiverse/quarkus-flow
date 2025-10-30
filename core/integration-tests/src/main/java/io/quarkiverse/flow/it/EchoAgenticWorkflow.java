package io.quarkiverse.flow.it;

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
                .tasks(t -> t.callFn("interactWithAI", f -> f.function(echoAgent::helloWorld)))
                .build();
    }

}
