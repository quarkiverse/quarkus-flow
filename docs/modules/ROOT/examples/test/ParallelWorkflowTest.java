package test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.acme.ExampleWorkflowsWireMockResource;
import org.acme.ParallelWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(ExampleWorkflowsWireMockResource.class)
public class ParallelWorkflowTest {

    @Inject
    ParallelWorkflow parallelWorkflow;

    @BeforeEach
    void resetWiremock() {
        WireMock.configureFor(8089);
        resetAllRequests();
    }

    @Test
    void testParallelBranchesExecute() throws Exception {
        // 1. Start the workflow
        WorkflowModel result = parallelWorkflow.instance()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        assertNotNull(result, "Workflow should complete successfully after joining parallel branches");

        // 2. Verify both endpoints were called exactly once!
        verify(1, postRequestedFor(urlEqualTo("/inventory-check")));
        verify(1, postRequestedFor(urlEqualTo("/credit-check")));
    }
}
