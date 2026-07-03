package org.acme.bestpractices;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.http;

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
                                .POST()
                                .endpoint("https://api.example.com/notify")
                                .header("Authorization", "Bearer eyJhbGciOi..."))
                .build();
    }
}
