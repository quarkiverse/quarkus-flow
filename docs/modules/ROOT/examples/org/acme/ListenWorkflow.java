package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class ListenWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("listen-to-one-workflow")
                .tasks(
                        // The workflow will pause here until the engine receives this specific CloudEvent
                        listen("waitForStartup", toOne("race.started.v1")),

                        // Once awakened, it executes this HTTP call
                        call("startup", post("", "http://localhost:8089/start")))
                .build();
    }
}
