package io.quarkiverse.flow.messaging.it;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
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

    @Inject
    @Channel("flow-in-outgoing")
    Emitter<byte[]> flowIn;

    @Test
    @DisplayName("greet_roundtrip_with_structured_cloud_event")
    void greet_roundtrip_structured() {
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, CloudEventDeserializer.class)
                .fromTopics("flow-out");

        final CloudEvent greet = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/it"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .withData("{\"name\":\"Elisa\"}".getBytes())
                .build();

        @SuppressWarnings("unchecked")
        ProducerRecord<Object, Object> structuredRecord = new ProducerRecord<>("flow-in",
                (Object) CE_JSON.serialize(greet));
        structuredRecord.headers().add("content-type", "application/cloudevents+json; charset=UTF-8".getBytes());
        companion.produceWithSerializers(StringSerializer.class, ByteArraySerializer.class)
                .fromRecords(structuredRecord);

        CloudEvent ce = awaitResponseCE(out);
        assertResponseCE(ce, "Elisa");
        out.close();
    }

    @Test
    @DisplayName("greet_roundtrip_with_binary_cloud_event")
    void greet_roundtrip_binary() {
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, CloudEventDeserializer.class)
                .fromTopics("flow-out");

        byte[] data = "{\"name\":\"Elisa\"}".getBytes();
        OutgoingCloudEventMetadata<?> ceMeta = OutgoingCloudEventMetadata.builder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/it"))
                .withType("io.quarkiverse.flow.messaging.hello.request")
                .withDataContentType("application/json")
                .build();

        flowIn.send(Message.of(data).addMetadata(ceMeta));

        CloudEvent ce = awaitResponseCE(out);
        assertResponseCE(ce, "Elisa");
        out.close();
    }

    private CloudEvent awaitResponseCE(ConsumerTask<Object, Object> out) {
        final String expectedType = "io.quarkiverse.flow.messaging.hello.response";
        final AtomicReference<CloudEvent> responseRef = new AtomicReference<>();

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            boolean found = out.stream()
                    .map(rec -> (CloudEvent) rec.value())
                    .filter(Objects::nonNull)
                    .peek(ce -> {
                        if (expectedType.equals(ce.getType()))
                            responseRef.set(ce);
                    })
                    .anyMatch(ce -> expectedType.equals(ce.getType()));
            assertThat(found).as("Still waiting for CE type: %s", expectedType).isTrue();
        });

        return responseRef.get();
    }

    private void assertResponseCE(CloudEvent ce, String name) {
        assertThat(ce).as("Response CloudEvent was not captured").isNotNull();
        assertThat(ce.getType()).isEqualTo("io.quarkiverse.flow.messaging.hello.response");
        assertThat(new String(Objects.requireNonNull(ce.getData()).toBytes()))
                .contains("\"Hello " + name + "!\"");
        assertThat(ce.getExtensionNames()).containsAll(List.of("custominstanceid", "customtaskid"));
    }

    public static class ConfigureMetadata implements QuarkusTestProfile {

        public ConfigureMetadata() {
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.messaging.metadata.instance-id.key", "custominstanceid",
                    "quarkus.flow.messaging.metadata.task-id.key", "customtaskid",
                    "mp.messaging.outgoing.flow-in-outgoing.connector", "smallrye-kafka",
                    "mp.messaging.outgoing.flow-in-outgoing.topic", "flow-in",
                    "mp.messaging.outgoing.flow-in-outgoing.value.serializer",
                    "org.apache.kafka.common.serialization.ByteArraySerializer");
        }
    }
}
