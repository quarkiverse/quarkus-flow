package org.acme;

// Static imports recommended for brevity:
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import java.util.Date;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class CronWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("cron-workflow")
                .schedule(cron("* * * * * ?")) // Every second
                .tasks(
                        set(Map.of("message", "Executed Cron Workflow at: " + new Date())))
                .build();
    }
}
