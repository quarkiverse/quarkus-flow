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
        Map<String, Object> input = Map.of("searchQuery", "luke",
                "acceptHeaderValue", "application/json");

        // 2. Execute the Parent workflow synchronously
        // The engine will automatically pause the parent, run EmitWorkflow,
        // resume, run ListenWorkflow, and finally complete.
        WorkflowModel result = parentWorkflow.instance(input)
                .start().join();

        // 3. Verify the execution finished without throwing subflow-resolution exceptions
        assertNotNull(result, "Parent workflow should successfully orchestrate and complete");
    }
}
