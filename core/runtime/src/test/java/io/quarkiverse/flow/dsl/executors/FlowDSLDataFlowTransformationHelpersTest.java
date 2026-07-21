package io.quarkiverse.flow.dsl.executors;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.input;
import static io.quarkiverse.flow.dsl.FlowDSL.output;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

public class FlowDSLDataFlowTransformationHelpersTest {

    @Test
    void test_input_with_inputFrom() {

        SoftAssertions softly = new SoftAssertions();

        Workflow workflow = FlowWorkflowBuilder.workflow("reviewSubmissionWithModel")
                .tasks(
                        function(
                                "add5",
                                (Long input) -> {
                                    softly.assertThat(input).isEqualTo(10L);
                                    return input + 5;
                                }),
                        function("returnEnriched", (Long enrichedValue) -> enrichedValue, Long.class)
                                .inputFrom(
                                        (Long object, WorkflowContextData workflowContext) -> {
                                            softly.assertThat(object).isEqualTo(15L);
                                            Long input = input(workflowContext, Long.class);
                                            softly.assertThat(input).isEqualTo(10L);
                                            return object + input;
                                        }))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowModel model = def.instance(10L).start().join();
            Number number = model.asNumber().orElseThrow();
            softly.assertThat(number.longValue()).isEqualTo(25L);
        }

        softly.assertAll();
    }

    @Test
    void test_input_with_outputAs() {

        SoftAssertions softly = new SoftAssertions();

        Workflow workflow = FlowWorkflowBuilder.workflow("enrichOutputWithModelTest")
                .tasks(
                        function(
                                "add5",
                                (Long input) -> {
                                    softly.assertThat(input).isEqualTo(10L);
                                    return input + 5;
                                },
                                Long.class)
                                .outputAs(
                                        (object, workflowContext, taskContextData) -> {
                                            softly.assertThat(object).isEqualTo(15L);
                                            Long input = input(workflowContext, Long.class);
                                            softly.assertThat(input).isEqualTo(10L);
                                            return input + object;
                                        },
                                        Long.class))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(workflow);

            WorkflowModel model = def.instance(10L).start().join();
            Number number = model.asNumber().orElseThrow();

            softly.assertThat(number.longValue()).isEqualTo(25L);
        }

        softly.assertAll();
    }

    @Test
    void test_output_with_exportAs() {

        SoftAssertions softly = new SoftAssertions();

        Workflow workflow = FlowWorkflowBuilder.workflow("enrichOutputWithInputTest")
                .tasks(
                        function(
                                "add5",
                                (Long input) -> {
                                    softly.assertThat(input).isEqualTo(10L);
                                    return input + 5;
                                },
                                Long.class)
                                .exportAs(
                                        (Long object,
                                                WorkflowContextData workflowContext,
                                                TaskContextData taskContextData) -> {
                                            Long taskOutput = output(taskContextData, Long.class);
                                            softly.assertThat(taskOutput).isEqualTo(15L);
                                            return taskOutput * 2;
                                        }))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowModel model = def.instance(10L).start().join();
            Number number = model.asNumber().orElseThrow();
            softly.assertThat(number.longValue()).isEqualTo(15L);
        }

        softly.assertAll();
    }

    @Test
    void test_input_with_inputFrom_fluent_way() {
        SoftAssertions softly = new SoftAssertions();

        Workflow workflow = FlowWorkflowBuilder.workflow("enrichOutputWithInputTest")
                .tasks(
                        function("sumFive", (Long input) -> input + 5, Long.class)
                                .inputFrom(
                                        (object, workflowContext, taskContextData) -> input(taskContextData, Long.class) * 2))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(workflow);
            WorkflowModel model = def.instance(10L).start().join();
            Number number = model.asNumber().orElseThrow();

            softly.assertThat(number.longValue()).isEqualTo(25L);
        }

        softly.assertAll();
    }

    @Test
    void test_input_with_exportAs() {

        SoftAssertions softly = new SoftAssertions();

        Workflow workflow = FlowWorkflowBuilder.workflow("enrichExportWithInputTest")
                .tasks(
                        function(
                                "add5",
                                (Long input) -> {
                                    softly.assertThat(input).isEqualTo(10L);
                                    return input + 5;
                                },
                                Long.class)
                                .exportAs(
                                        (Long object,
                                                WorkflowContextData workflowContext,
                                                TaskContextData taskContextData) -> {
                                            Long taskOutput = output(taskContextData, Long.class);
                                            softly.assertThat(taskOutput).isEqualTo(15L);
                                            Long input = input(workflowContext, Long.class);
                                            softly.assertThat(input).isEqualTo(10L);
                                            return input + taskOutput;
                                        }))
                .build();

        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            WorkflowDefinition def = app.workflowDefinition(workflow);

            WorkflowInstance instance = def.instance(10L);
            instance.start().join();
            Number number = instance.context().asNumber().orElseThrow();

            softly.assertThat(number.longValue()).isEqualTo(25L);
        }

        softly.assertAll();
    }
}
