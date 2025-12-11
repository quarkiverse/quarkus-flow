package io.quarkiverse.flow.camel.slack;

import static io.quarkiverse.flow.camel.FlowCamelDSL.camel;
import static io.quarkiverse.flow.camel.slack.FlowCamelSlackDSL.slack;
import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class SlackNotificationWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow()
                .tasks(set("${ \"Hello \" + .name }"),
                        camel(slack("alerts", "slack.webhook.team1"), String.class))
                .build();
    }
}
