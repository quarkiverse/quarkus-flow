package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.acme.ExampleWorkflowsWireMockResource;
import org.acme.OpenApiWorkflow;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(ExampleWorkflowsWireMockResource.class)
public class OpenApiWorkflowTest {

    @Inject
    OpenApiWorkflow openApiWorkflow;

    @Test
    void testOpenApiWorkflow() throws ExecutionException, InterruptedException, TimeoutException {
        // Execute workflow (no inputs required for this hardcoded example)
        WorkflowModel result = openApiWorkflow.instance(Map.of())
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        // 2. Unpack the state directly as a Jackson JsonNode
        JsonNode rootNode = result.as(JsonNode.class)
                .orElseThrow(() -> new IllegalStateException("Workflow result is empty"));

        // 3. Assertions using the Jackson Tree Model
        assertTrue(rootNode.isArray(), "The result state should be a JSON Array");
        assertEquals(1, rootNode.size(), "Should return exactly 1 mocked pet");

        // Extract the first object in the array
        JsonNode firstPet = rootNode.get(0);

        // Use Jackson's .asInt() and .asText() for safe assertions
        assertEquals(101, firstPet.get("id").asInt());
        assertEquals("Mocked Doggo", firstPet.get("name").asText());
        assertEquals("available", firstPet.get("status").asText());
    }
}
