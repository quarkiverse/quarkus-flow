package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.acme.ExampleEvent;
import org.acme.TaskContextWorkflow;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
public class TaskContextWorkflowTest {

    @Inject
    TaskContextWorkflow taskContextWorkflow;

    @Test
    void testTaskAndWorkflowContext() throws Exception {
        ExampleEvent input = new ExampleEvent("System Boot Sequence");

        WorkflowModel result = taskContextWorkflow.instance(input)
                .start().join();

        assertNotNull(result, "Workflow should complete successfully");

        // Verify the task context data was successfully appended
        String finalState = result.asText().orElseThrow();
        assertEquals("Audited [System Boot Sequence] via task: taskAudit", finalState,
                "The engine should inject the task name into the lambda execution");
    }
}
