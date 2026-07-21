package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.withContext;
import static io.quarkiverse.flow.dsl.TestSerializationUtils.writeAndReadInMemory;
import static io.serverlessworkflow.api.WorkflowWriter.workflowAsBytes;
import static io.serverlessworkflow.api.WorkflowWriter.workflowAsString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;

public class FlowDSLSerializationTest {

    @ParameterizedTest
    @MethodSource("workflows")
    public void testSpecFeaturesParsing(Workflow workflow, Consumer<WorkflowDefinition> assertion)
            throws IOException {
        Workflow otherWorkflow = writeAndReadInMemory(workflow);
        assertWorkflowEquals(workflow, otherWorkflow);
        try (WorkflowApplication application = WorkflowApplication.builder().build()) {
            assertion.accept(application.workflowDefinition(otherWorkflow));
        }
    }

    static Stream<Arguments> workflows() {
        final int QUANTITY = 3;
        return Stream.of(
                Arguments.of(
                        FlowWorkflowBuilder.workflow("hello")
                                .tasks(t -> t.set("sayHelloWorld", b -> b.expr(Map.of("result", "hello world!"))))
                                .build(),
                        new CheckResult(Map.of(), Map.of("result", "hello world!"))),
                Arguments.of(
                        FlowWorkflowBuilder.workflow("inc")
                                .tasks(function(FlowDSLSerializationTest::inc))
                                .build(),
                        new CheckResult(1, 2)),
                Arguments.of(
                        FlowWorkflowBuilder.workflow("incContext")
                                .tasks(withContext(FlowDSLSerializationTest::incContext))
                                .build(),
                        new CheckResult(1, 3)),
                Arguments.of(
                        FlowWorkflowBuilder.workflow("incLambda")
                                .tasks(function((Integer number) -> number + QUANTITY))
                                .build(),
                        new CheckResult(1, 4)));
    }

    private static class CheckResult implements Consumer<WorkflowDefinition> {

        private final Object input;
        private final Object output;

        public CheckResult(Object input, Object output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public void accept(WorkflowDefinition t) {
            assertThat(t.instance(this.input).start().join().asJavaObject()).isEqualTo(this.output);
        }
    }

    private static void assertWorkflowEquals(Workflow workflow, Workflow other) throws IOException {
        assertThat(workflowAsString(workflow, WorkflowFormat.YAML))
                .isEqualTo(workflowAsString(other, WorkflowFormat.YAML));
        assertThat(workflowAsBytes(workflow, WorkflowFormat.JSON))
                .isEqualTo(workflowAsBytes(other, WorkflowFormat.JSON));
    }

    private static Integer inc(Integer quantity) {
        return quantity + 1;
    }

    private static Integer incContext(Integer quantity, WorkflowContextData workflowContext) {
        return quantity + 2;
    }
}
