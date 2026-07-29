package org.acme.newsletter;

import static io.restassured.RestAssured.given;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.jackson.JsonUtils;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.acme.newsletter.agents.AutoDraftCriticAgent;
import org.acme.newsletter.agents.HumanEditorAgent;
import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterDraft;
import org.acme.newsletter.domain.NewsletterRequest;
import org.acme.newsletter.services.MailService;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.ArgumentCaptor;

@DisabledOnOs(OS.WINDOWS)
@QuarkusTest
@TestProfile(NewsletterWorkflowIT.BroadcastProfile.class)
public class NewsletterWorkflowIT {

    @Inject
    @Channel("flow-out-incoming")
    io.smallrye.mutiny.Multi<Message<byte[]>> flowOutEvents;

    @InjectMock
    MailService mailService;

    @InjectMock
    AutoDraftCriticAgent draftAgent;

    @InjectMock
    HumanEditorAgent humanEditorAgent;

    @BeforeEach
    void setupAiMocks() {
        NewsletterDraft initialDraft = new NewsletterDraft("Bullish Market Update", "Fed cuts taxes...",
                "Full body text...");

        NewsletterDraft revisedDraft = new NewsletterDraft("Cautious Market Update", "Fed considers tax cuts...",
                "Toned down body text...");

        when(draftAgent.write(anyString(), any())).thenReturn(initialDraft);

        when(humanEditorAgent.edit(any())).thenReturn(revisedDraft);
    }

    @Test
    void agent_chain_human_review_two_rounds_via_rest() {
        final String expectedType = "org.acme.email.review.required";
        final List<NewsletterDraft> capturedDrafts = new CopyOnWriteArrayList<>();
        final AtomicReference<String> instanceIdRef = new AtomicReference<>();

        // Subscribe to flow-out events to capture review-required events
        flowOutEvents.subscribe().with(msg -> {
            try {
                CloudEventMetadata<?> ceMeta = msg.getMetadata(CloudEventMetadata.class).orElse(null);
                if (ceMeta != null && expectedType.equals(ceMeta.getType())) {
                    NewsletterDraft draft = parseNewsletterDraft(msg.getPayload());
                    if (draft != null) {
                        capturedDrafts.add(draft);
                        String flowInstanceId = ceMeta.getExtension("flowinstanceid")
                                .orElseThrow(() -> new IllegalStateException("flowinstanceid not presented in the event"))
                                .toString();
                        instanceIdRef.compareAndSet(null, flowInstanceId);
                    }
                }
                msg.ack();
            } catch (Exception e) {
                // ignore non-matching events
            }
        });

        final NewsletterRequest request = new NewsletterRequest(NewsletterRequest.MarketMood.BULLISH,
                List.of("IBM:-13%", "GOOGL:+5%"), "Fed is about to cut taxes, software companies to move up",
                NewsletterRequest.Tone.CAUTIOUS, NewsletterRequest.Length.SHORT);

        // 1) start via REST
        given().contentType("application/json").body(request).when().post("/api/newsletter").then().statusCode(202);

        // 2) ROUND #1 — wait first review-required
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(capturedDrafts).as("Should receive first review-required event").isNotEmpty();
        });
        assertThat(capturedDrafts.get(0)).isNotNull();
        assertThat(instanceIdRef.get()).isNotEmpty();

        // 3) needs_revision -> loop back to drafter, passing the correlation ID
        sendHumanReview(instanceIdRef.get(),
                new HumanReview(capturedDrafts.get(0), "Please tone down the hype",
                        HumanReview.ReviewStatus.NEEDS_REVISION));

        // 4) ROUND #2 — wait NEXT review-required
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            assertThat(capturedDrafts).as("Should receive second review-required event")
                    .hasSizeGreaterThanOrEqualTo(2);
        });
        assertThat(capturedDrafts.get(1)).isNotNull();

        // 5) done -> workflow proceeds to sendNewsletter
        sendHumanReview(instanceIdRef.get(), new HumanReview(capturedDrafts.get(1), "", HumanReview.ReviewStatus.DONE));

        // 6) verify MailService was called with some non-empty body
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            ArgumentCaptor<NewsletterDraft> bodyCaptor = ArgumentCaptor.forClass(NewsletterDraft.class);
            verify(mailService, atLeastOnce()).send(eq("subscribers@acme.finance.org"), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).isNotNull();
        });
    }

    private void sendHumanReview(String instanceId, HumanReview review) {
        // REST wrapper sends CloudEvent to flow-in.
        // We pass the instanceId so the API can attach it as the 'flowinstanceid' CE extension.
        given()
                .header("X-Flow-Instance-Id", instanceId)
                .contentType("application/json").body(review).when().put("/api/newsletter").then().statusCode(202);
    }

    private NewsletterDraft parseNewsletterDraft(byte[] data) {
        try {
            if (data == null || data.length == 0)
                return null;
            return JsonUtils.mapper().readValue(data, NewsletterDraft.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse NewsletterDraft from event data", e);
        }
    }

    public static class BroadcastProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("mp.messaging.incoming.flow-out-incoming.broadcast", "true");
        }
    }
}
