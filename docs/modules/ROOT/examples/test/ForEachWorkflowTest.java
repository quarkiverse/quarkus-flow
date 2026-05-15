package test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.inject.Inject;

import org.acme.ExampleWorkflowsWireMockResource;
import org.acme.ForEachWorkflow;
import org.acme.Order;
import org.acme.OrdersPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(ExampleWorkflowsWireMockResource.class)
public class ForEachWorkflowTest {

    @Inject
    ForEachWorkflow forEachWorkflow;

    @BeforeEach
    void resetWiremock() {
        WireMock.configureFor(8089);
        resetAllRequests();
    }

    @Test
    void testForEachIteration() {
        OrdersPayload input = new OrdersPayload(List.of(
                new Order("ORD-001"),
                new Order("ORD-002"),
                new Order("ORD-003")));

        // 2. Execute the workflow synchronously
        WorkflowModel result = forEachWorkflow.instance(input).start().join();

        assertNotNull(result, "Workflow should complete successfully");

        // 3. Verify the engine looped and executed the HTTP task exactly 3 times!
        verify(3, postRequestedFor(urlEqualTo("/process-order")));

        // 4. Only the result of last task can should be in result
        assertThat(result.asText().orElseThrow(), is("{\"processed_orders_status\":\"success\"}"));
    }
}
