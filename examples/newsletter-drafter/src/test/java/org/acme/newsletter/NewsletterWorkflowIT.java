package org.acme.newsletter;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterDraft;
import org.acme.newsletter.domain.NewsletterRequest;
import org.acme.newsletter.services.MailService;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.ArgumentCaptor;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;

import static io.restassured.RestAssured.given;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

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

    @Test
    void agent_chain_human_review_two_rounds_via_rest() {
        // Start consuming BEFORE triggering the workflow
        ConsumerTask<Object, Object> out = companion
                .consumeWithDeserializers(StringDeserializer.class, ByteArrayDeserializer.class).fromTopics("flow-out");

        final String expectedType = "org.acme.email.review.required";
        final AtomicReference<NewsletterDraft> draft1 = new AtomicReference<>();
        final AtomicReference<NewsletterDraft> draft2 = new AtomicReference<>();
        final AtomicLong firstReviewOffset = new AtomicLong(-1L);

        // Store the correlation ID
        final AtomicReference<String> instanceIdRef = new AtomicReference<>();

        final NewsletterRequest request = new NewsletterRequest(
                NewsletterRequest.MarketMood.BULLISH,
                List.of("IBM:-13%", "GOOGL:+5%"),
                "Fed is about to cut taxes, software companies to move up",
                NewsletterRequest.Tone.CAUTIOUS,
                NewsletterRequest.Length.SHORT);

        // 1) start via REST
        given().contentType("application/json").body(request).when().post("/api/newsletter").then()
                .statusCode(202);

        // 2) ROUND #1 — wait first review-required and capture its offset AND instanceId
        await().atMost(ofSeconds(120)).untilAsserted(() -> {
            boolean found = out.stream().anyMatch(rec -> {
                CloudEvent ce = CE_JSON.deserialize((byte[]) rec.value());
                if (expectedType.equals(ce.getType())) {
                    draft1.set(parseNewsletterDraft(ce));
                    firstReviewOffset.set(rec.offset());

                    // Extract the flowinstanceid extension added by Quarkus Flow
                    Object flowInstanceId = ce.getExtension("flowinstanceid");
                    assertThat(flowInstanceId).isNotNull();
                    instanceIdRef.set(flowInstanceId.toString());

                    return true;
                }
                return false;
            });
            assertThat(found).isTrue();
        });
        assertThat(draft1.get()).isNotNull();
        assertThat(instanceIdRef.get()).isNotEmpty();

        // 3) needs_revision -> loop back to drafter, passing the correlation ID
        sendHumanReview(instanceIdRef.get(),
                new HumanReview(draft1.get(), "Please tone down the hype", HumanReview.ReviewStatus.NEEDS_REVISION));

        // 4) ROUND #2 — wait NEXT review-required (offset strictly greater)
        await().atMost(ofSeconds(60)).untilAsserted(() -> {
            boolean found = out.stream().anyMatch(rec -> {
                if (rec.offset() <= firstReviewOffset.get())
                    return false;
                CloudEvent ce = CE_JSON.deserialize((byte[]) rec.value());
                if (expectedType.equals(ce.getType())) {
                    draft2.set(parseNewsletterDraft(ce));
                    return true;
                }
                return false;
            });
            assertThat(found).isTrue();
        });
        assertThat(draft2.get()).isNotNull();

        // 5) done -> workflow proceeds to sendNewsletter
        sendHumanReview(instanceIdRef.get(), new HumanReview(draft2.get(), "", HumanReview.ReviewStatus.DONE));

        // 6) verify MailService was called with some non-empty body
        await().atMost(ofSeconds(30)).untilAsserted(() -> {
            ArgumentCaptor<NewsletterDraft> bodyCaptor = ArgumentCaptor.forClass(NewsletterDraft.class);
            verify(mailService, atLeastOnce()).send(eq("subscribers@acme.finance.org"), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).isNotNull();
        });

        out.close();
    }

    private void sendHumanReview(String instanceId, HumanReview review) {
        // REST wrapper sends CloudEvent to flow-in.
        // We pass the instanceId so the API can attach it as the 'flowinstanceid' CE extension.
        given()
                .contentType("application/json")
                .body(review)
                .when()
                .put("/api/newsletter/" + instanceId)
                .then()
                .statusCode(202);
    }

    private NewsletterDraft parseNewsletterDraft(CloudEvent ce) {
        try {
            byte[] data = ce.getData() == null ? null : ce.getData().toBytes();
            if (data == null)
                return null;
            return JsonUtils.mapper().readValue(data, NewsletterDraft.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse NewsletterDraft from CloudEvent data", e);
        }
    }
}