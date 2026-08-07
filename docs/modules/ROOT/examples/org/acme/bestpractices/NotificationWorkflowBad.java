package org.acme.bestpractices;

import static io.quarkiverse.flow.dsl.FlowDSL.http;
import static io.quarkiverse.flow.dsl.FlowWorkflowBuilder.workflow;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class NotificationWorkflowBad extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("notify")
                .tasks(
                        // NEVER hard-code tokens in the workflow definition
                        http("callApi")
                                .post()
                                .endpoint("https://api.example.com/notify")
                                .header("X-Api-Key", "my-api-key"))
                .build();
    }
}
