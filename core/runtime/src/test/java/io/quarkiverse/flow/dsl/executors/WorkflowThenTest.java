package io.quarkiverse.flow.dsl.executors;

import static io.quarkiverse.flow.dsl.FlowDSL.consume;
import static io.quarkiverse.flow.dsl.FlowDSL.function;

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;

public class WorkflowThenTest {

    private static final Logger log = LoggerFactory.getLogger(WorkflowThenTest.class);

    @Test
    void consume_then_skips_next_task_and_jumps_to_target() {

        Workflow wf = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(
                        consume("sendNewsletter", input -> log.debug("Consuming: {}", input))
                                .then("otherTask"),
                        function("nextTask", v -> "nextTask: " + v, String.class),
                        function("otherTask", v -> "otherTask: " + v, String.class))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(wf);
            WorkflowModel model = def.instance("hello newsletter").start().join();

            String output = model.asText().orElseThrow();
            Assertions.assertEquals("otherTask: hello newsletter", output);
        }
    }

    @Test
    void function_then_skips_next_task_and_jumps_to_target() {

        Workflow wf = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(
                        function("arrayFromString", input -> input.split(","), String.class)
                                .then("otherTask"),
                        function("nextTask", arr -> "nextTask: " + Arrays.toString(arr), String[].class),
                        function("otherTask", arr -> "otherTask: " + Arrays.toString(arr), String[].class))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(wf);
            String output = def.instance("hello,from,cncf").start().join().asText().orElseThrow();

            Assertions.assertEquals("otherTask: [hello, from, cncf]", output);
        }
    }

    @Test
    void function_then_end_directive_stops_workflow_execution() {

        Workflow wf = FlowWorkflowBuilder.workflow("intelligent-newsletter")
                .tasks(
                        function("uppercase", String::toUpperCase, String.class)
                                .then(FlowDirectiveEnum.END),
                        function("lowercase", String::toLowerCase, String.class))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(wf);
            String output = def.instance("Hello Alice, Hello Bob, Hello Everyone!")
                    .start()
                    .join()
                    .asText()
                    .orElseThrow();

            Assertions.assertEquals("HELLO ALICE, HELLO BOB, HELLO EVERYONE!", output);
        }
    }
}
