package org.acme.newsletter;

import static io.restassured.RestAssured.given;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.acme.newsletter.domain.CriticAgentReview;
import org.acme.newsletter.services.MailService;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.ArgumentCaptor;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
public class NewsletterWorkflowIT {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @InjectMock
    MailService mailService;

    private static String jsonEsc(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void agent_chain_human_review_two_rounds_via_rest() {
        // Start consuming BEFORE triggering the workflow
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, ByteArrayDeserializer.class)
                .fromTopics("flow-out");

        final String expectedType = "org.acme.email.review.required";
        final AtomicReference<CriticAgentReview> critic1 = new AtomicReference<>();
        final AtomicReference<CriticAgentReview> critic2 = new AtomicReference<>();
        final AtomicLong firstReviewOffset = new AtomicLong(-1L);

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

        // 1) start via REST
        given().contentType("application/json").body(initialInput)
                .when().post("/api/newsletter")
                .then().statusCode(202);

        // 2) ROUND #1 — wait first review-required and capture its offset
        await().atMost(ofSeconds(60)).untilAsserted(() -> {
            boolean found = out.stream()
                    .anyMatch(rec -> {
                        CloudEvent ce = CE_JSON.deserialize((byte[]) rec.value());
                        if (expectedType.equals(ce.getType())) {
                            critic1.set(parseCriticFrom(ce));
                            firstReviewOffset.set(rec.offset());
                            return true;
                        }
                        return false;
                    });
            assertThat(found).isTrue();
        });
        assertThat(critic1.get()).isNotNull();

        // 3) needs_revision -> loop back to drafter
        sendHumanReview(critic1.get().getOriginalDraft(), "Please tone down hype.", "NEEDS_REVISION");

        // 4) ROUND #2 — wait NEXT review-required (offset strictly greater)
        await().atMost(ofSeconds(60)).untilAsserted(() -> {
            boolean found = out.stream()
                    .anyMatch(rec -> {
                        if (rec.offset() <= firstReviewOffset.get()) return false;
                        CloudEvent ce = CE_JSON.deserialize((byte[]) rec.value());
                        if (expectedType.equals(ce.getType())) {
                            critic2.set(parseCriticFrom(ce));
                            return true;
                        }
                        return false;
                    });
            assertThat(found).isTrue();
        });
        assertThat(critic2.get()).isNotNull();

        // 5) done -> workflow proceeds to sendNewsletter
        sendHumanReview(critic2.get().getOriginalDraft(), "", "DONE");

        // 6) verify MailService was called with some non-empty body
        await().atMost(ofSeconds(30)).untilAsserted(() -> {
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(mailService, atLeastOnce())
                    .send(eq("subscribers@acme.finance.org"), eq("Weekly Newsletter"), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).isNotBlank();
        });

        out.close();
    }

    private void sendHumanReview(String draft, String notes, String status) {
        // REST wrapper sends CloudEvent to flow-in
        given()
                .contentType("application/json")
                .body("""
                        {
                          "draft": %s,
                          "notes": %s,
                          "status": %s
                        }
                        """.formatted(jsonEsc(draft), jsonEsc(notes), jsonEsc(status)))
                .when()
                .put("/api/newsletter")
                .then()
                .statusCode(202);
    }

    private CriticAgentReview parseCriticFrom(CloudEvent ce) {
        try {
            byte[] data = ce.getData() == null ? null : ce.getData().toBytes();
            if (data == null) return null;
            return io.serverlessworkflow.impl.jackson.JsonUtils.mapper()
                    .readValue(data, CriticAgentReview.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse CriticAgentReview from CloudEvent data", e);
        }
    }
}
