package io.quarkiverse.flow.dsl;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

public class FuncCallAsyncTest {

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private CompletableFuture<Integer> waitAsync(Integer waitTime) {
        return CompletableFuture.supplyAsync(
                () -> {
                    safeSleep(waitTime);
                    return 1;
                });
    }

    private Integer waitSync(Integer waitTime) {
        safeSleep(waitTime);
        return 1;
    }

    @Test
    void testCompletableCall() throws IOException {
        runIt(FlowWorkflowBuilder.workflow("waitCompletable").tasks(function(this::waitAsync)).build());
    }

    @Test
    void testReferencedFunctionCall() throws IOException {
        runIt(FlowWorkflowBuilder.workflow("waitReference").tasks(function(this::waitSync)).build());
    }

    @Test
    void testLambdaCall() throws IOException {
        runIt(FlowWorkflowBuilder.workflow("waitLambda").tasks(function(v -> 1)).build());
    }

    private class TimeListener implements WorkflowExecutionListener {

        private AtomicLong startTime = new AtomicLong();

        @Override
        public void onWorkflowStarted(WorkflowStartedEvent ev) {
            startTime.set(System.currentTimeMillis());
        }
    }

    private void runIt(Workflow workflow) throws IOException {
        TimeListener listener = new TimeListener();
        try (WorkflowApplication app = WorkflowApplication.builder().withListener(listener).build()) {
            final long waitTime = 200;
            WorkflowInstance instance = app.workflowDefinition(TestSerializationUtils.writeAndReadInMemory(workflow))
                    .instance(waitTime);
            CompletableFuture<WorkflowModel> future = instance.start();
            assertThat(System.currentTimeMillis() - listener.startTime.get()).isLessThan(waitTime);
            assertThat(future.join().asNumber().map(Number::intValue).orElseThrow()).isEqualTo(1);
        }
    }
}
