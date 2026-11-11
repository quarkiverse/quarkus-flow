package io.quarkiverse.flow.persistence.test.durable;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.waitSeconds;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowModel;

@ApplicationScoped
public class RecoveryWaitWorkflow extends Flow {

    public static final String WAIT_TASK_NAME = "napDecision";

    public static final int WAIT_SECONDS = 20;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("recoveryWaitWorkflow")
                .tasks(
                        function("waitTask1", o -> {
                            RecoveryWaitResource.TASK1_TIMES.incrementAndGet();
                            return o.asMap();
                        }, WorkflowModel.class),
                        function("waitTask2", o -> {
                            RecoveryWaitResource.TASK2_TIMES.incrementAndGet();
                            return o.asMap();
                        }, WorkflowModel.class),
                        waitSeconds(WAIT_TASK_NAME, WAIT_SECONDS),
                        function("waitTask4", o -> {
                            RecoveryWaitResource.TASK4_TIMES.incrementAndGet();
                            return "success";
                        }, WorkflowModel.class))
                .build();
    }
}
