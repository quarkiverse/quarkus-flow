package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.ListenTask;
import io.serverlessworkflow.api.types.ListenTaskConfiguration;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;

class FlowDSLListenFirstTest {

    @Test
    @DisplayName("listen(toOne(type).first()) sets read=DATA and output transform")
    void listen_toOne_first_sets_read_data_and_output() {
        Workflow wf = FlowWorkflowBuilder.workflow("listen-first")
                .tasks(
                        listen(toOne("org.acme.event").first()),
                        function("process", String::trim, String.class))
                .build();

        List<TaskItem> items = wf.getDo();
        Task t = items.get(0).getTask();
        ListenTask lt = t.getListenTask();
        assertNotNull(lt, "ListenTask expected");

        assertEquals(
                ListenTaskConfiguration.ListenAndReadAs.DATA,
                lt.getListen().getRead(),
                "read should be DATA (default)");
        assertNotNull(lt.getOutput(), "Output should be set (from first)");
        assertNotNull(lt.getOutput().getAs(), "Output.as should be set");
        assertNotNull(lt.getOutput().getAs().getObject(),
                "Output.as should contain a Java function for unwrapping");
    }

    @Test
    @DisplayName("listen(toOne(type).envelope().first()) sets read=ENVELOPE and output transform")
    void listen_toOne_envelope_first_sets_read_envelope_and_output() {
        Workflow wf = FlowWorkflowBuilder.workflow("listen-envelope-first")
                .tasks(
                        listen(toOne("org.acme.event").envelope().first()),
                        function("process", String::trim, String.class))
                .build();

        List<TaskItem> items = wf.getDo();
        Task t = items.get(0).getTask();
        ListenTask lt = t.getListenTask();
        assertNotNull(lt, "ListenTask expected");

        assertEquals(
                ListenTaskConfiguration.ListenAndReadAs.ENVELOPE,
                lt.getListen().getRead(),
                "read should be ENVELOPE");
        assertNotNull(lt.getOutput(), "Output should be set (from envelope().first())");
        assertNotNull(lt.getOutput().getAs(), "Output.as should be set");
    }

    @Test
    @DisplayName("listen(toOne(type)) without first does NOT set output transform")
    void listen_toOne_without_first_does_not_set_output() {
        Workflow wf = FlowWorkflowBuilder.workflow("listen-no-first")
                .tasks(
                        listen(toOne("org.acme.event")),
                        function("process", String::trim, String.class))
                .build();

        List<TaskItem> items = wf.getDo();
        Task t = items.get(0).getTask();
        ListenTask lt = t.getListenTask();
        assertNotNull(lt, "ListenTask expected");

        assertNull(lt.getOutput(), "Output should NOT be set when first is not called");
    }
}
