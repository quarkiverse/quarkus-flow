package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;

public class ForkFuncTest {

    private int value = 2;

    public int getValue() {
        return value;
    }

    @Test
    void testForkVerbose() throws IOException {
        testIt(
                FlowWorkflowBuilder.workflow("parallel-execution-workflow")
                        .tasks(
                                funcTaskItemListBuilder -> funcTaskItemListBuilder.fork(
                                        funcForkTaskBuilder -> funcForkTaskBuilder.branches(
                                                inner -> {
                                                    inner.function(f -> f.function(this::doubleIt));
                                                    inner.function(f -> f.function(this::halfIt));
                                                })))
                        .build());
    }

    @Test
    void testForkSyntaxSugar() throws IOException {
        testIt(
                FlowWorkflowBuilder.workflow("parallel-execution-workflow")
                        .tasks(fork(function(this::doubleIt), function(this::halfIt)))
                        .build());
    }

    private void testIt(Workflow workflow) throws IOException {
        workflow = TestSerializationUtils.writeAndReadInMemory(workflow);
        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            assertThat(
                    app.workflowDefinition(workflow).instance(8).start().join().asCollection().stream()
                            .flatMap(m -> m.asMap().orElseThrow().values().stream())
                            .toList())
                    .containsExactlyInAnyOrder(2, 18);
        }
    }

    private int doubleIt(int number) {
        return (number << 1) + value;
    }

    private int halfIt(int number) {
        return (number >> 1) - value;
    }
}
