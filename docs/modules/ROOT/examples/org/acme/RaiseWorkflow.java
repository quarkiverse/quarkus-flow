package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.raise;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class RaiseWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("raise-workflow")
                .tasks(
                        raise("notImplemented", r -> r
                                .error(e -> e
                                        .type("https://serverlessworkflow.io/errors/not-implemented")
                                        .status(501)
                                        .title("Not Implemented"))))
                .build();
    }
}
