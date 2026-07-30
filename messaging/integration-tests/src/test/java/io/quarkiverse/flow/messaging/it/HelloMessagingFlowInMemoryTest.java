package io.quarkiverse.flow.messaging.it;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;

/**
 * Tests CloudEvent emission using the in-memory connector, resembling
 * the pattern used in end-user tests (e.g. TripPlannerFlowTest from issue #810).
 * <p>
 * Validates that:
 * <ul>
 * <li>{@code InMemorySink<String>} receives String payloads (not byte[])</li>
 * <li>CloudEvent metadata is accessible via {@link CloudEventMetadata}</li>
 * <li>The payload can be deserialized as a structured CloudEvent JSON</li>
 * </ul>
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/810">Issue #810</a>
 */
@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@TestProfile(HelloMessagingFlowInMemoryTest.InMemoryProfile.class)
public class HelloMessagingFlowInMemoryTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    HelloMessagingFlow workflow;

    @BeforeEach
    void setUp() {
        connector.sink("flow-out").clear();
        workflow.instance(Map.of()).start();
    }

    @Test
    @DisplayName("greet_roundtrip_with_in_memory_connector_produces_string_payload")
    void greet_roundtrip_in_memory() {
        InMemorySink<String> sink = connector.sink("flow-out");
        InMemorySource<Message<?>> source = connector.source("flow-in");

        OutgoingCloudEventMetadata<?> ceMeta = OutgoingCloudEventMetadata.builder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/in-memory"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .build();

        source.send(Message.of("{\"name\":\"InMemory\"}".getBytes()).addMetadata(ceMeta));

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<? extends Message<String>> messages = sink.received();
            assertThat(messages)
                    .as("Should receive at least one event on flow-out")
                    .isNotEmpty();

            Message<String> msg = messages.stream()
                    .filter(m -> {
                        CloudEventMetadata<?> ce = m.getMetadata(CloudEventMetadata.class).orElse(null);
                        return ce != null && "io.quarkiverse.flow.messaging.hello.response".equals(ce.getType());
                    })
                    .findFirst()
                    .orElse(null);

            assertThat(msg).as("Should find a response CloudEvent").isNotNull();

            CloudEventMetadata<?> responseMeta = msg.getMetadata(CloudEventMetadata.class).orElseThrow();
            assertThat(responseMeta.getType()).isEqualTo("io.quarkiverse.flow.messaging.hello.response");

            String payload = msg.getPayload();
            assertThat(payload)
                    .as("Payload should be a String (not byte[]) containing the greeting")
                    .isInstanceOf(String.class)
                    .contains("\"Hello InMemory!\"");
        });
    }

    @Test
    @DisplayName("in_memory_payload_is_valid_json")
    void in_memory_payload_is_valid_json() {
        InMemorySink<String> sink = connector.sink("flow-out");
        InMemorySource<Message<?>> source = connector.source("flow-in");

        OutgoingCloudEventMetadata<?> ceMeta = OutgoingCloudEventMetadata.builder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/in-memory"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .build();

        source.send(Message.of("{\"name\":\"JsonCheck\"}".getBytes()).addMetadata(ceMeta));

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            List<? extends Message<String>> messages = sink.received();
            assertThat(messages).isNotEmpty();

            Message<String> msg = messages.stream()
                    .filter(m -> {
                        CloudEventMetadata<?> ce = m.getMetadata(CloudEventMetadata.class).orElse(null);
                        return ce != null && "io.quarkiverse.flow.messaging.hello.response".equals(ce.getType());
                    })
                    .findFirst()
                    .orElse(null);

            assertThat(msg).isNotNull();

            String payload = msg.getPayload();
            assertThat(payload).startsWith("{").endsWith("}");
            assertThat(payload).contains("\"Hello JsonCheck!\"");
        });
    }

    public static class InMemoryProfile implements QuarkusTestProfile {

        public InMemoryProfile() {
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "mp.messaging.incoming.flow-in.connector", "smallrye-in-memory",
                    "mp.messaging.outgoing.flow-out.connector", "smallrye-in-memory",
                    "mp.messaging.outgoing.flow-lifecycle-out.connector", "smallrye-in-memory",
                    "quarkus.kafka.devservices.enabled", "false");
        }
    }
}
