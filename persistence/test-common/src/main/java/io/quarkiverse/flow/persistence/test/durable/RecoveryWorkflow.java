package io.quarkiverse.flow.persistence.test.durable;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowModel;

@ApplicationScoped
public class RecoveryWorkflow extends Flow {

    public static final String EVENT_NAME = "org.acme.user.recovery.Decision";

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("recoveryWorkflow")
                .tasks(
                        function("task1", o -> {
                            RecoveryResource.TASK1_TIMES.incrementAndGet();
                            return o.asMap();
                        }, WorkflowModel.class),
                        function("task2", o -> {
                            RecoveryResource.TASK2_TIMES.incrementAndGet();
                            return o.asMap();
                        }, WorkflowModel.class),
                        listen("waitDecision", toOne(EVENT_NAME)),
                        function("task4", o -> {
                            RecoveryResource.TASK4_TIMES.incrementAndGet();
                            return "after-decision";
                        }, WorkflowModel.class),
                        function("task5", o -> {
                            RecoveryResource.TASK5_TIMES.incrementAndGet();
                            return "success";
                        }, WorkflowModel.class))
                .build();
    }
}
