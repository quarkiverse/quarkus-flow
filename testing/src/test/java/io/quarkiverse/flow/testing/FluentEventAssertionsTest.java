package io.quarkiverse.flow.testing;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class FluentEventAssertionsTest {


    @Test
    void should_wait_for_workflow_completion() {
        Workflow workflow = FuncWorkflowBuilder.workflow()
                .tasks(
                        FuncDSL.function("inc", (number) -> number + 1, Long.class)
                )
                .build();

        WorkflowEventStore workflowEventStore = new WorkflowEventStore();

        try (WorkflowApplication app = WorkflowApplication.builder()
                .withListener(new TestWorkflowExecutionListener(workflowEventStore))
                .build()) {

            WorkflowDefinition def = app.workflowDefinition(workflow);

            WorkflowInstance workflowInstance = def.instance(10L);

            WorkflowModel workflowModel = workflowInstance.start().join();

            FluentEventAssertions.assertThat(workflowEventStore)
                    .workflowStarted()
                    .taskStarted("inc")
                    .taskCompleted("inc")
                    .assertAll();
        }

    }
}
