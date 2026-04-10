package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.acme.HttpOauth2Workflow;
import org.acme.SecureWireMockResource;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
@QuarkusTestResource(SecureWireMockResource.class)
public class HttpOauth2WorkflowTest {

    @Inject
    HttpOauth2Workflow httpOauth2Workflow;

    @Test
    void testOAuth2SecuredCall() throws Exception {
        // 1. Map input to resolve {petId}
        Map<String, Object> input = Map.of("petId", 99);

        // 2. Execute workflow
        WorkflowModel result = httpOauth2Workflow.instance(input)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        // 3. Extract the native Jackson JsonNode
        JsonNode rootNode = result.as(JsonNode.class)
                .orElseThrow(() -> new IllegalStateException("Workflow result is empty"));

        // 4. Validate the engine successfully fetched and utilized the token
        assertFalse(rootNode.isEmpty(), "The result state should not be empty");
        assertEquals(99, rootNode.get("id").asInt());
        assertEquals("Secure Doggo", rootNode.get("name").asText());
    }
}
