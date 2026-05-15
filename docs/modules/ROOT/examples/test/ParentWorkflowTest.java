package test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import jakarta.inject.Inject;

import org.acme.ParentWorkflow;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
public class ParentWorkflowTest {

    @Inject
    ParentWorkflow parentWorkflow;

    @Test
    void testParentOrchestratesChildrenSuccessfully() throws Exception {
        // Define input, so that HttpWorkflow invoked as subflows has necesarry data
        Map<String, Object> input = Map.of("searchQuery", "luke",
                "acceptHeaderValue", "application/json");

        // Execute the Parent workflow
        WorkflowModel result = parentWorkflow.instance(input)
                .start().join();

        // Verify the execution finished without throwing subflow-resolution exceptions
        assertNotNull(result, "Parent workflow should successfully orchestrate and complete");
    }
}
