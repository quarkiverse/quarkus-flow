package test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.acme.ConditionalWorkflow;
import org.acme.ExampleWorkflowsWireMockResource;
import org.acme.ScorePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(ExampleWorkflowsWireMockResource.class)
public class ConditionalWorkflowTest {

    @Inject
    ConditionalWorkflow conditionalWorkflow;

    @BeforeEach
    void resetWiremock() {
        // Tell the static WireMock client where our mock server actually lives!
        WireMock.configureFor(8089);

        // Now it will successfully send the reset command to port 8089
        resetAllRequests();
    }

    @Test
    void testApprovedPath() throws Exception {
        // 1. Provide a passing score
        ScorePayload input = new ScorePayload(85);

        // 2. Execute
        WorkflowModel result = conditionalWorkflow.instance(input).start().join();

        assertNotNull(result, "Workflow should complete successfully");

        // 3. Verify the engine routed to the 'approve' task and skipped 'reject'
        verify(1, postRequestedFor(urlEqualTo("/approve")));
        verify(0, postRequestedFor(urlEqualTo("/reject")));
    }

    @Test
    void testRejectedPath() throws Exception {
        // 1. Provide a failing score
        ScorePayload input = new ScorePayload(60);

        // 2. Execute
        WorkflowModel result = conditionalWorkflow.instance(input).start().join();

        assertNotNull(result, "Workflow should complete successfully");

        // 3. Verify the engine routed to the 'reject' task and skipped 'approve'
        verify(0, postRequestedFor(urlEqualTo("/approve")));
        verify(1, postRequestedFor(urlEqualTo("/reject")));
    }
}
