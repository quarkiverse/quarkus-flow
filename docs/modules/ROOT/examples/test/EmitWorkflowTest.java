package test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.acme.EmitWorkflow;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class EmitWorkflowTest {

    @Inject
    EmitWorkflow emitWorkflow;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testEmitEvent() throws Exception {
        // 1. Grab the simulated 'flow-out' sink
        InMemorySink<byte[]> sink = connector.sink("flow-out");
        sink.clear();

        // 2. Provide input data that perfectly maps to the fields of Message.class
        Map<String, Object> input = Map.of(
                "message", "placed"
        );

        // 3. Execute the workflow synchronously
        WorkflowModel result = emitWorkflow.instance(input)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        assertNotNull(result, "Workflow should complete successfully");

        // 4. Verify the sink received the event
        var receivedMessages = sink.received();
        assertEquals(1, receivedMessages.size(), "Should have emitted exactly one event");

        // 5. Unpack the byte array into a JSON Node
        byte[] payload = receivedMessages.get(0).getPayload();
        JsonNode eventNode = objectMapper.readTree(payload);

        // 6. Assert standard CloudEvent headers
        assertEquals("com.petstore.order.placed.v1", eventNode.get("type").asText());
        assertNotNull(eventNode.get("source"), "Engine should auto-generate a source");

        // 7. Assert the strongly-typed data block matches our Message structure
        JsonNode dataNode = eventNode.get("data");
        assertEquals("placed", dataNode.get("message").asText());
    }
}