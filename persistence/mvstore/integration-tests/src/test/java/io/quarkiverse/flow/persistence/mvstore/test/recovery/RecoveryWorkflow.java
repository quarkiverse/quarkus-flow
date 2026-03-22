package io.quarkiverse.flow.persistence.mvstore.test.recovery;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.to;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class RecoveryWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return workflow(RecoveryTestConstants.WORKFLOW_NAME)
                .tasks(
                        function("task1", RecoveryFunctions::passThrough, Map.class),
                        function("task2", RecoveryFunctions::passThrough, Map.class),
                        listen("wait",
                                to().one(event -> event.with(props -> props.type("\"" + RecoveryTestConstants.RESUME_EVENT_TYPE + "\"")
                                        .source("\"" + RecoveryTestConstants.RESUME_EVENT_SOURCE + "\"")))),
                        function("task4", RecoveryFunctions::passThrough, Map.class),
                        function("task5", RecoveryFunctions::passThrough, Map.class))
                .build();
    }
}
