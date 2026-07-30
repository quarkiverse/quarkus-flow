package io.quarkiverse.flow.messaging.it;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.vertx.core.json.JsonObject;

/**
 * Tests that CloudEvents work correctly in <strong>structured</strong> mode,
 * where the entire CE envelope (attributes + data) is serialized as a single
 * JSON value, as opposed to binary mode where attributes go in Kafka headers.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/810">Issue #810</a>
 */
@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@TestProfile(HelloMessagingFlowStructuredIT.StructuredCEProfile.class)
public class HelloMessagingFlowStructuredIT {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    @Channel("flow-in-outgoing")
    Emitter<String> flowIn;

    @Inject
    HelloMessagingFlow workflow;

    @BeforeEach
    void setUp() {
        workflow.instance(java.util.Map.of()).start();
    }

    @Test
    @DisplayName("greet_roundtrip_structured_mode_with_structured_input")
    void greet_roundtrip_structured_input() {
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, StringDeserializer.class)
                .fromTopics("flow-out");

        final CloudEvent greet = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/it-structured"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .withData("{\"name\":\"Structured\"}".getBytes())
                .build();

        ProducerRecord<Object, Object> structuredRecord = new ProducerRecord<>("flow-in",
                new String(CE_JSON.serialize(greet), StandardCharsets.UTF_8));
        structuredRecord.headers().add("content-type", "application/cloudevents+json; charset=UTF-8".getBytes());
        companion.produceWithSerializers(StringSerializer.class, StringSerializer.class)
                .fromRecords(structuredRecord);

        String ceJson = awaitResponseJson(out, "Structured");
        assertStructuredCeResponse(ceJson, "Structured");
    }

    @Test
    @DisplayName("greet_roundtrip_structured_mode_with_binary_input")
    void greet_roundtrip_binary_input() {
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, StringDeserializer.class)
                .fromTopics("flow-out");

        String data = "{\"name\":\"Binary\"}";
        OutgoingCloudEventMetadata<?> ceMeta = OutgoingCloudEventMetadata.builder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/it-structured"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .build();

        flowIn.send(Message.of(data).addMetadata(ceMeta));

        String ceJson = awaitResponseJson(out, "Binary");
        assertStructuredCeResponse(ceJson, "Binary");
    }

    private void assertStructuredCeResponse(String ceJson, String name) {
        JsonObject envelope = new JsonObject(ceJson);
        assertThat(envelope.getString("type"))
                .as("CE type attribute")
                .isEqualTo("io.quarkiverse.flow.messaging.hello.response");

        Object rawData = envelope.getValue("data");
        String dataJson = rawData instanceof String ? (String) rawData : rawData.toString();
        assertThat(dataJson)
                .as("CE data should contain the greeting for %s", name)
                .contains("Hello " + name + "!");
    }

    private String awaitResponseJson(ConsumerTask<Object, Object> out, String expectedName) {
        final String expectedType = "io.quarkiverse.flow.messaging.hello.response";
        final String expectedGreeting = "Hello " + expectedName + "!";
        final AtomicReference<String> responseRef = new AtomicReference<>();

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            boolean found = out.stream()
                    .map(rec -> (String) rec.value())
                    .filter(Objects::nonNull)
                    .peek(json -> {
                        if (json.contains(expectedType) && json.contains(expectedGreeting))
                            responseRef.set(json);
                    })
                    .anyMatch(json -> json.contains(expectedType) && json.contains(expectedGreeting));
            assertThat(found).as("Still waiting for structured CE with greeting: %s", expectedGreeting).isTrue();
        });

        return responseRef.get();
    }

    public static class StructuredCEProfile implements QuarkusTestProfile {

        public StructuredCEProfile() {
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "mp.messaging.outgoing.flow-out.cloud-events-mode", "structured",
                    "mp.messaging.outgoing.flow-out.value.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer",
                    "mp.messaging.outgoing.flow-lifecycle-out.cloud-events-mode", "structured",
                    "mp.messaging.outgoing.flow-lifecycle-out.value.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer",
                    "mp.messaging.outgoing.flow-in-outgoing.connector", "smallrye-kafka",
                    "mp.messaging.outgoing.flow-in-outgoing.topic", "flow-in",
                    "mp.messaging.outgoing.flow-in-outgoing.value.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer");
        }
    }
}
