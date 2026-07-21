package org.acme;

import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;
import static io.quarkiverse.flow.dsl.FlowDSL.get;
import static io.quarkiverse.flow.dsl.FlowDSL.set;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FaultedWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("faulted-workflow", "quarkus.flow").tasks(get("http://localhost:9899")) // there is no server
                // running at port 9899
                .build();
    }
}
