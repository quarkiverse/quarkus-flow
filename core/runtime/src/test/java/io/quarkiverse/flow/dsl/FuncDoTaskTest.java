package io.quarkiverse.flow.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

public class FuncDoTaskTest {

    @Test
    void testDoTaskRaiseAndTryCatch() throws IOException {
        try (WorkflowApplication app = WorkflowApplication.builder().build()) {

            var workflow = FlowWorkflowBuilder.workflow("test-do-task")
                    .tasks(
                            doTask -> doTask.tryCatch(
                                    "try-catch-task",
                                    tryBuilder -> tryBuilder
                                            .tryHandler(
                                                    tryBlock -> tryBlock.raise(
                                                            "raise-error-task",
                                                            raiseBlock -> raiseBlock.error(
                                                                    error -> error
                                                                            .type(
                                                                                    URI.create(
                                                                                            "http://example.com/error"))
                                                                            .status(500))))
                                            .catchHandler(
                                                    catchBlock -> catchBlock
                                                            .errorsWith(
                                                                    errs -> errs.type("http://example.com/error"))
                                                            .doTasks(
                                                                    catchTasks -> catchTasks.set(
                                                                            "catchHandled",
                                                                            setBlock -> setBlock.expr(
                                                                                    Map.of("handled", true)))))))
                    .build();

            WorkflowInstance instance = app.workflowDefinition(TestSerializationUtils.writeAndReadInMemory(workflow))
                    .instance(Map.of());

            CompletableFuture<WorkflowModel> future = instance.start();
            WorkflowModel result = future.join();

            assertThat(instance.status()).isEqualTo(WorkflowStatus.COMPLETED);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result.as(Map.class).orElseThrow();
            assertThat(resultMap)
                    .containsEntry("handled", true)
                    .containsKey("handled");
        }
    }
}
