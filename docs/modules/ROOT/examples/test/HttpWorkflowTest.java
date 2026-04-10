package test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;

import org.acme.HttpWorkflow;
import org.acme.ExampleWorkflowsWireMockResource;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(ExampleWorkflowsWireMockResource.class)
public class HttpWorkflowTest {

    @Inject
    HttpWorkflow httpWorkflow;

    @Test
    void testHttpWorkflow() throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, Object> input = Map.of("searchQuery", "luke",
                                           "acceptHeaderValue", "application/json");

        WorkflowModel result = httpWorkflow.instance(input)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        assertTrue((Integer) result.asMap().orElseThrow().get("count") > 0,
                "Should find at least one character");

        List<Map<String, Object>> characters = (List<Map<String, Object>>) result.asMap().orElseThrow().get("results");
        assertFalse(characters.isEmpty(),
                "The results list should not be empty");

        String firstCharacterName = (String) characters.get(0).get("name");
        assertTrue(firstCharacterName.toLowerCase().contains("luke"),
                "The returned character name should contain 'luke'");

    }
}
