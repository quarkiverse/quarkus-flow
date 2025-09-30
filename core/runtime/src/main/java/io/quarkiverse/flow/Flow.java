package io.quarkiverse.flow;

import java.util.concurrent.CompletionStage;

import io.quarkus.arc.Arc;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

public abstract class Flow {

    /**
     * Workflow descriptor you can describe via the CNCF Java SDK DSL.
     */
    public abstract Workflow descriptor();

    protected WorkflowDefinition definition() {
        return Arc.container()
                .select(WorkflowDefinition.class, Identifier.Literal.of(this.getClass().getName()))
                .get();
    }

    public WorkflowInstance instance(Object in) {
        return definition().instance(in);
    }

    public CompletionStage<WorkflowModel> startInstance(Object in) {
        return instance(in).start();
    }
}
