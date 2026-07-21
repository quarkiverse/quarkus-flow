package org.acme;

// Static imports recommended for brevity:
import static io.quarkiverse.flow.dsl.FlowDSL.*;

import java.util.Date;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class CronWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("cron-workflow")
                .schedule(cron("* * * * * ?")) // Every second
                .tasks(
                        set(Map.of("message", "Executed Cron Workflow at: " + new Date())))
                .build();
    }
}
