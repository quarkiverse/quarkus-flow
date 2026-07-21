package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class ParallelWorkflow extends Flow {

    @ConfigProperty(name = "wiremock.url")
    String wiremockUrl;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("parallel-workflow-using-branches")
                .tasks(
                        fork("checkInventoryAndCredit",
                                http("checkInventory")
                                        .method("POST")
                                        .body("")
                                        .endpoint(wiremockUrl + "/inventory-check"),
                                http("checkCredit")
                                        .method("POST")
                                        .body("")
                                        .endpoint(wiremockUrl + "/credit-check")))
                .build();
    }
}
