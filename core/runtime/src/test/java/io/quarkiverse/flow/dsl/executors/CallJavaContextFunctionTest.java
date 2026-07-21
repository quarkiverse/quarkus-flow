package io.quarkiverse.flow.dsl.executors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.dsl.types.CallJava;
import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;

class CallJavaContextFunctionTest {

    // Reuse the same Person type used in CallTest
    record Person(String name, int age) {
    }

    @Test
    void testJavaContextFunction_simple() throws InterruptedException, ExecutionException {
        try (WorkflowApplication app = WorkflowApplication.builder().build()) {
            var ctxFn = (ContextFunction<Person, String>) (person, workflowContext) -> person.name
                    + "@"
                    + workflowContext.definition().workflow().getDocument().getName();

            Workflow workflow = new Workflow()
                    .withDocument(
                            new Document()
                                    .withNamespace("test")
                                    .withName("testJavaContextCall")
                                    .withVersion("1.0"))
                    .withDo(
                            List.of(
                                    new TaskItem(
                                            "javaContextCall",
                                            new Task()
                                                    .withCallTask(
                                                            new CallTask()
                                                                    .withCallFunction(
                                                                            CallJava.function(ctxFn, Person.class))))));

            var out = app.workflowDefinition(workflow)
                    .instance(new Person("Elisa", 30))
                    .start()
                    .get()
                    .asText()
                    .orElseThrow();

            assertThat(out).isEqualTo("Elisa@testJavaContextCall");
        }
    }
}
