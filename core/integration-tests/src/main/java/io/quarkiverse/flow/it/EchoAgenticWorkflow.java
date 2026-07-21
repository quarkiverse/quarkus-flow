package io.quarkiverse.flow.it;

import static io.quarkiverse.flow.dsl.FlowDSL.function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class EchoAgenticWorkflow extends Flow {

    @Inject
    EchoAgent echoAgent;

    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("workflowEchoAgentic")
                .tasks(function("interactWithAI", echoAgent::helloWorld, String.class))
                .build();
    }

}
