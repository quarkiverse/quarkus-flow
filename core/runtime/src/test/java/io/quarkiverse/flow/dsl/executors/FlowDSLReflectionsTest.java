package io.quarkiverse.flow.dsl.executors;

import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static io.quarkiverse.flow.dsl.FlowDSL.withContext;
import static io.quarkiverse.flow.dsl.FlowDSL.withFilter;
import static io.quarkiverse.flow.dsl.FlowDSL.withInstanceId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContextData;

public class FlowDSLReflectionsTest {

    @Test
    void check_serializable_function() {
        Workflow wf = FlowWorkflowBuilder.workflow("strip-function").tasks(function(String::strip)).build();
        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            Optional<String> output = app.workflowDefinition(wf).instance("Hello World!     ").start().join().asText();
            assertTrue(output.isPresent());
            assertEquals("Hello World!", output.get());
        }
    }

    @Test
    void check_serializable_function_with_non_serializable_capture() {
        // 1. Create a clearly non-serializable object
        class NonSerializableService {
            String appendSuffix(String text) {
                return text + " - Processed successfully";
            }
        }

        NonSerializableService service = new NonSerializableService();

        Workflow wf = FlowWorkflowBuilder.workflow("capture-function")
                .tasks(function(service::appendSuffix))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            Optional<String> output = app.workflowDefinition(wf).instance("Test Input").start().join().asText();

            assertTrue(output.isPresent());
            assertEquals("Test Input - Processed successfully", output.get());
        }
    }

    @Test
    void check_serializable_predicate_switch() {
        Workflow wf = FlowWorkflowBuilder.workflow("predicate-test")
                .tasks(
                        // Infers Integer.class automatically
                        switchWhenOrElse((Integer v) -> v > 10, "highValueTask", FlowDirectiveEnum.END),
                        // Only executes if > 10
                        function("highValueTask", (Integer v) -> v * 2))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            // Test True path
            Optional<Integer> highOutput = app.workflowDefinition(wf).instance(15).start().join().as(Integer.class);

            assertTrue(highOutput.isPresent());
            assertEquals(30, highOutput.get());

            // Test False path (ends immediately, returning original input)
            Optional<Integer> lowOutput = app.workflowDefinition(wf).instance(5).start().join().as(Integer.class);

            assertTrue(lowOutput.isPresent());
            assertEquals(5, lowOutput.get());
        }
    }

    @Test
    void check_serializable_unique_id_bifunction() {
        Workflow wf = FlowWorkflowBuilder.workflow("agent-unique-id-test")
                .tasks(
                        // Infers String.class for the payload (the second parameter)
                        agent(
                                (String uniqueId, Integer payload) -> "ID=[" + uniqueId + "] Payload=[" + payload + "]"))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            Optional<String> output = app.workflowDefinition(wf).instance(123).start().join().asText();

            assertTrue(output.isPresent());
            // The uniqueId should contain the workflow instance ID and the JSON pointer
            assertTrue(output.get().contains("ID=["));
            assertTrue(output.get().contains("] Payload=[123]"));
        }
    }

    @Test
    void check_serializable_java_context_function() {
        Workflow wf = FlowWorkflowBuilder.workflow("context-function-test")
                .tasks(
                        // Infers String.class for the payload (the first parameter)
                        withContext(
                                (String payload, WorkflowContextData wctx) -> payload + " processed by "
                                        + wctx.instanceData().id()))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            Optional<String> output = app.workflowDefinition(wf).instance("Context Data").start().join().asText();

            assertTrue(output.isPresent());
            assertTrue(output.get().startsWith("Context Data processed by "));
        }
    }

    @Test
    void check_serializable_java_filter_function() {
        Workflow wf = FlowWorkflowBuilder.workflow("filter-function-test")
                .tasks(
                        // Infers String.class for the payload (the first parameter)
                        withFilter(
                                (String payload, WorkflowContextData wctx, TaskContextData tctx) -> payload + " at position "
                                        + tctx.position().jsonPointer()))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            Optional<String> output = app.workflowDefinition(wf).instance("Filter Data").start().join().asText();

            assertTrue(output.isPresent());
            // It should append the task JSON pointer (likely "/tasks/0" or similar depending on spec)
            assertTrue(output.get().startsWith("Filter Data at position do/"));
        }
    }

    @Test
    void check_serializable_instance_id_function() {
        Workflow wf = FlowWorkflowBuilder.workflow("instance-id-test")
                .tasks(
                        // Infers Integer.class for the payload (the second parameter)
                        withInstanceId(
                                "",
                                (String instanceId, Integer payload) -> "Instance=[" + instanceId + "] Payload=[" + payload
                                        + "]"))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            Optional<String> output = app.workflowDefinition(wf).instance(456).start().join().asText();

            assertTrue(output.isPresent());
            // The instanceId should be populated automatically by the workflow runtime
            assertTrue(output.get().contains("Instance=["));
            assertTrue(output.get().contains("] Payload=[456]"));
        }
    }
}
