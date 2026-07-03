package org.acme.bestpractices;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.http;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class NotificationWorkflowGood extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow("notify")
                .use(u -> u.secrets("mySecrets"))
                .tasks(
                        // NEVER hard-code tokens in the workflow definition
                        http("callApi")
                                .POST()
                                .endpoint("https://api.example.com/notify")
                                .header("X-Api-Key", "${ $secret.mySecrets.apiKey }"))
                .build();
    }
}
