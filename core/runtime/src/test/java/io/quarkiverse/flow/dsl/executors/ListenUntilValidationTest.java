package io.quarkiverse.flow.dsl.executors;

import static io.quarkiverse.flow.dsl.FlowDSL.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;

public class ListenUntilValidationTest {

    @Test
    public void testUntilWithAllThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (WorkflowApplication app = WorkflowApplication.builder().build()) {
                        Workflow workflow = FlowWorkflowBuilder.workflow("test-all-until-invalid")
                                .tasks(
                                        listen(
                                                "waitOrders",
                                                toAll("order.created")
                                                        .until(
                                                                (io.serverlessworkflow.impl.WorkflowModelCollection events) -> events
                                                                        .stream().count() >= 3,
                                                                io.serverlessworkflow.impl.WorkflowModelCollection.class)))
                                .build();

                        app.workflowDefinition(workflow);
                    }
                });

        assertTrue(exception.getMessage().contains("until() is only supported with any()"));
        assertTrue(exception.getMessage().contains("ALL"));
    }

    @Test
    public void testUntilWithOneThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (WorkflowApplication app = WorkflowApplication.builder().build()) {
                        Workflow workflow = FlowWorkflowBuilder.workflow("test-one-until-invalid")
                                .tasks(
                                        listen(
                                                "waitOrders",
                                                toOne("order.created")
                                                        .until(
                                                                (io.serverlessworkflow.impl.WorkflowModelCollection events) -> events
                                                                        .stream().count() >= 3,
                                                                io.serverlessworkflow.impl.WorkflowModelCollection.class)))
                                .build();

                        app.workflowDefinition(workflow);
                    }
                });

        assertTrue(exception.getMessage().contains("until() is only supported with any()"));
        assertTrue(exception.getMessage().contains("ONE"));
    }
}
