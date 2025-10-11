package org.acme.newsletter;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.acme.newsletter.domain.CriticOutput;
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
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
public class NewsletterWorkflowTest {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    NewsletterWorkflow workflow;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    private static String jsonEsc(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void agent_chain_human_review_two_rounds() throws Exception {
        // Start consuming BEFORE we start the workflow so we don't miss the event
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, ByteArrayDeserializer.class)
                .fromTopics("flow-out");

        final String initialInput = """
                {
                  "marketMood": "bullish",
                  "topMovers": "AAPL:+3%, MSFT:+2%",
                  "macroData": "CPI cooling; jobs steady",
                  "tone": "friendly",
                  "length": "short",
                  "notes": ""
                }
                """;

        var instanceFuture = workflow.instance(initialInput).start(); // pauses at listen

        // Round 1: wait the first email.review.required
        final String expectedType = "org.acme.email.review.required";
        final AtomicReference<CriticOutput> critic1 = new AtomicReference<>();

        await().atMost(ofSeconds(20)).untilAsserted(() -> {
            boolean found = out.stream()
                    .map(rec -> CE_JSON.deserialize((byte[]) rec.value()))
                    .anyMatch(ce -> {
                        if (expectedType.equals(ce.getType())) {
                            CriticOutput co = CE_JSON
                                    .deserialize((byte[]) out.getLastRecord().value())
                                    .getData() == null ? null
                                    : parseCriticFrom(ce);
                            if (co != null) critic1.set(co);
                            return true;
                        }
                        return false;
                    });
            assertThat(found).isTrue();
        });

        assertThat(critic1.get()).isNotNull();

        // Send human review event with status needs_revision → loop back to drafter
        sendHumanReview(critic1.get().getOriginalDraft(),
                "Please tone down hype.",
                "needs_revision");

        // Round 2: expect another email.review.required due to loop
        final AtomicReference<CriticOutput> critic2 = new AtomicReference<>();

        await().atMost(ofSeconds(20)).untilAsserted(() -> {
            boolean found = out.stream()
                    .map(rec -> CE_JSON.deserialize((byte[]) rec.value()))
                    .anyMatch(ce -> {
                        if (expectedType.equals(ce.getType())) {
                            CriticOutput co = parseCriticFrom(ce);
                            if (co != null) critic2.set(co);
                            return true;
                        }
                        return false;
                    });
            assertThat(found).isTrue();
        });

        assertThat(critic2.get()).isNotNull();

        // Send human review event with status done → workflow finishes
        sendHumanReview(critic2.get().getOriginalDraft(),
                "",
                "done");

        // Await completion; expect the last result to be a CriticOutput (from last critic pass)
        Map<String, Object> result = instanceFuture.get(30, TimeUnit.SECONDS)
                .asMap()
                .orElseThrow(() -> new AssertionError("No final output"));

        assertThat(result).isNotNull();

        // tidy up
        out.close();
    }

    private void sendHumanReview(String draft, String notes, String status) {
        final String body = """
                { "draft": %s, "notes": %s, "status": %s }
                """.formatted(jsonEsc(draft), jsonEsc(notes), jsonEsc(status));

        CloudEvent reviewEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("test:/newsletter"))
                .withType("org.acme.newsletter.review.done")
                .withDataContentType("application/json")
                .withData(body.getBytes())
                .build();

        byte[] payload = CE_JSON.serialize(reviewEvent);
        companion.produceWithSerializers(StringSerializer.class, ByteArraySerializer.class)
                .fromRecords(new ProducerRecord<>("flow-in", payload));
    }

    private CriticOutput parseCriticFrom(CloudEvent ce) {
        try {
            byte[] data = ce.getData() == null ? null : ce.getData().toBytes();
            if (data == null) return null;
            // The workflow emitted PojoCloudEventData<CriticOutput>, but it was serialized as JSON bytes.
            // Just bind it using the same Jackson type the producer used (CriticOutput).
            return io.serverlessworkflow.impl.jackson.JsonUtils.mapper()
                    .readValue(data, CriticOutput.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse CriticOutput from CloudEvent data", e);
        }
    }
}
