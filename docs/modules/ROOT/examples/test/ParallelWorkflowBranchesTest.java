package test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.acme.ExampleWorkflowsWireMockResource;
import org.acme.ParallelWorkflowBranches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(ExampleWorkflowsWireMockResource.class)
public class ParallelWorkflowBranchesTest {

    @Inject
    ParallelWorkflowBranches parallelWorkflowBranches;

    @BeforeEach
    void resetWiremock() {
        WireMock.configureFor(8089);
        resetAllRequests();
    }

    @Test
    void testParallelWorkflowBranchesExecute() {
        // 1. Start the workflow
        WorkflowModel result = parallelWorkflowBranches.instance().start().join();

        assertNotNull(result, "Workflow should complete successfully after joining parallel branches");

        // 2. Verify both endpoints were called exactly once!
        verify(1, postRequestedFor(urlEqualTo("/inventory-check")));
        verify(1, postRequestedFor(urlEqualTo("/credit-check")));
    }
}
