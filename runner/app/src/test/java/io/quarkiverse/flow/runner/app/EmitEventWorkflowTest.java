package io.quarkiverse.flow.runner.app;

import static io.restassured.RestAssured.given;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;

/**
 * Tests that the emit-event workflow correctly publishes events to the in-memory flow-out channel.
 */
@QuarkusTest
@TestProfile(EmitEventWorkflowTest.MessagingProfile.class)
class EmitEventWorkflowTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    void setUp() {
        // Clear any messages from previous tests
        connector.sink("flow-out").clear();
    }

    @Test
    @DisplayName("emit_event_workflow_publishes_to_flow_out_channel")
    void emit_event_workflow_publishes_to_flow_out_channel() {
        // Given
        Map<String, Object> input = Map.of("name", "Test User");

        // When - Execute the emit-event workflow synchronously
        ExecutionResponse response = given()
                .contentType("application/json")
                .body(input)
                .queryParam("wait", "true")
                .when()
                .post("/q/flow/exec/quarkiverse-flow-runner/emit-event/0.1.0")
                .then()
                .statusCode(200)
                .extract()
                .as(ExecutionResponse.class);

        // Then - Verify workflow completed
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(WorkflowStatus.COMPLETED);

        // And - Verify event was emitted to flow-out channel
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            InMemorySink<String> sink = connector.sink("flow-out");

            List<? extends Message<String>> messages = sink.received();
            assertThat(messages)
                    .as("Events should have been emitted to flow-out channel")
                    .isNotEmpty();

            Message<String> msg = messages.get(0);
            CloudEventMetadata<?> ceMeta = msg.getMetadata(CloudEventMetadata.class).orElse(null);
            assertThat(ceMeta).as("Message should carry CloudEvent metadata").isNotNull();
            assertThat(ceMeta.getType()).isEqualTo("org.quarkiverse.flow.runner.app.response");
            assertThat(ceMeta.getSource().toString()).isEqualTo("uri://org.quarkiverse.flow.runner.app.test");

            assertThat(msg.getPayload()).contains("Test User");
        });
    }

    /**
     * Test profile for messaging tests with unique database path to avoid file locks.
     */
    public static class MessagingProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Use unique database file per test class to avoid MVStore file locks
            String uniqueDbPath = "target/test-db-" + System.currentTimeMillis() + "-" +
                    System.nanoTime() + ".mv";
            return Map.of(
                    "quarkus.flow.persistence.mvstore.db-path", uniqueDbPath,
                    "quarkus.flow.runner.security.type", "none");
        }
    }
}
