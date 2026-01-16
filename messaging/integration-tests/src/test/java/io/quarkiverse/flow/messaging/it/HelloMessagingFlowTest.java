package io.quarkiverse.flow.messaging.it;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@TestProfile(HelloMessagingFlowTest.ConfigureMetadata.class)
public class HelloMessagingFlowTest {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Test
    void greet_roundtrip() {
        // Start consuming from 'flow-out' BEFORE producing to avoid missing anything
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, ByteArrayDeserializer.class)
                .fromTopics("flow-out");

        // Produce the domain CloudEvent to 'flow-in'
        final CloudEvent greet = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/it"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .withData("{\"name\":\"Elisa\"}".getBytes())
                .build();

        final byte[] payload = CE_JSON.serialize(greet);

        companion.produceWithSerializers(StringSerializer.class, ByteArraySerializer.class)
                .fromRecords(new ProducerRecord<>("flow-in", payload));

        // Await until we see OUR domain response event on 'flow-out'
        final String expectedType = "io.quarkiverse.flow.messaging.hello.response";
        final AtomicReference<CloudEvent> responseRef = new AtomicReference<>();

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            boolean found = out.stream()
                    .map(rec -> CE_JSON.deserialize((byte[]) rec.value()))
                    .peek(ce -> {
                        if (expectedType.equals(ce.getType()))
                            responseRef.set(ce);
                    })
                    .anyMatch(ce -> expectedType.equals(ce.getType()));
            assertTrue(found, "Still waiting for CE type: " + expectedType);
        });

        CloudEvent ce = responseRef.get();
        assertNotNull(ce, "Response CloudEvent was not captured");
        assertEquals(expectedType, ce.getType());

        // Validate payload content (adjust to your workflow’s behavior)
        assertTrue(new String(ce.getData().toBytes()).contains("\"Hello Elisa!\""),
                "Unexpected CE data: " + new String(ce.getData().toBytes()));

        assertTrue(ce.getExtensionNames().containsAll(List.of("custominstanceid", "customtaskid")));

        // Tidy up
        out.close();
    }

    public static class ConfigureMetadata implements QuarkusTestProfile {

        public ConfigureMetadata() {
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.messaging.metadata.instance-id.key", "custominstanceid",
                    "quarkus.flow.messaging.metadata.task-id.key", "customtaskid");
        }
    }
}
