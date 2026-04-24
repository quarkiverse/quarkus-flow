package test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.acme.ContextWorkflow;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
public class ContextWorkflowTest {

    @Inject
    ContextWorkflow contextWorkflow;

    @Test
    void testContextAwareExecution() throws Exception {
        // 1. Provide a standard String payload, as expected by String.class
        String input = "Test-Data-123";

        // 2. Execute the workflow synchronously
        WorkflowModel result = contextWorkflow.instance(input)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        // 3. Verify the engine completed the execution
        assertNotNull(result, "Workflow should complete successfully");

        assertNotNull(result.asText().orElseThrow());
        assertThat(result.asText().orElseThrow(), is("Processed Test-Data-123"));
    }
}
