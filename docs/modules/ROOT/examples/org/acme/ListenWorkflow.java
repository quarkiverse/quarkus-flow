package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class ListenWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("listen-to-one-workflow")
                .tasks(
                        // The workflow will pause here until the engine receives this specific CloudEvent
                        listen("waitForStartup", toOne("race.started.v1")),

                        // Once awakened, it executes this HTTP call
                        call("startup", post("", wiremockUrl + "/start")))
                .build();
    }
}
