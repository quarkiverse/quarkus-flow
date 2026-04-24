package test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.acme.ExampleWorkflowsWireMockResource;
import org.acme.ListenWorkflow;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;

@QuarkusTest
@QuarkusTestResource(ExampleWorkflowsWireMockResource.class)
public class ListenWorkflowTest {

    @Inject
    ListenWorkflow listenWorkflow;

    // Inject the SmallRye In-Memory bridge
    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    void testListenForEvent() throws Exception {
        var workflowInstance = listenWorkflow.instance(Map.of());
        String instanceId = workflowInstance.id();

        CompletableFuture<WorkflowModel> futureResult = workflowInstance
                .start()
                .toCompletableFuture();

        // 2. Grab the simulated 'flow-in' channel
        InMemorySource<byte[]> source = connector.source("flow-in");

        // 3. Construct a standard CloudEvent JSON payload
        String cloudEventJson = """
                {
                    "specversion": "1.0",
                    "id": "%s",
                    "source": "test-framework",
                    "type": "race.started.v1",
                    "flowinstanceid": "%s",
                    "datacontenttype": "application/json",
                    "data": {
                        "track": "Monaco"
                    }
                }
                """.formatted(UUID.randomUUID().toString(), instanceId);

        // 4. Fire the event into the channel!
        source.send(cloudEventJson.getBytes(StandardCharsets.UTF_8));

        // 3. Block and wait for the workflow to wake up, call WireMock, and finish
        WorkflowModel result = futureResult.get(1, TimeUnit.SECONDS);

        assertNotNull(result, "Workflow should successfully complete after being awakened by the event");
    }
}
