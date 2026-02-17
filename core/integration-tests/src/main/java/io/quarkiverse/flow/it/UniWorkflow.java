package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UniWorkflow extends Flow {

    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("uniTest")
                .tasks(tasks -> tasks.function(f -> f.function(t -> Uni.createFrom().item("Javierito"))))
                .build();
    }
}
