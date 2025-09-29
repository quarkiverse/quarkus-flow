package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.flow.FlowDescriptor;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HelloAgenticWorkflow {

    @Inject
    HelloAgent helloAgent;

    @FlowDescriptor
    public Workflow helloAgenticWorkflow() {
        return FuncWorkflowBuilder.workflow()
                .tasks(t -> t.callFn(f -> f.function(helloAgent::helloWorld)))
                .build();
    }

}
