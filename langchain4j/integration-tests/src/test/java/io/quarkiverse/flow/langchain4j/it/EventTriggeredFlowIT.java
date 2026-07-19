package io.quarkiverse.flow.langchain4j.it;

import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.startOllamaMock;
import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.stubConferenceReviewerImprover;
import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.stubConferenceReviewerScore;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import io.cloudevents.core.v1.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.EventPublisher;

@QuarkusTest
@QuarkusTestResource(value = EventTriggeredFlowIT.OllamaMockResource.class, restrictToAnnotatedClass = true)
public class EventTriggeredFlowIT {

    public static class OllamaMockResource implements QuarkusTestResourceLifecycleManager {

        private WireMockServer wireMock;

        @Override
        public Map<String, String> start() {
            wireMock = startOllamaMock();
            stubConferenceReviewerImprover(wireMock);
            stubConferenceReviewerScore(wireMock);
            return Map.of("quarkus.langchain4j.ollama.base-url", wireMock.baseUrl());
        }

        @Override
        public void stop() {
            if (wireMock != null) {
                wireMock.stop();
            }
        }
    }

    @Inject
    WorkflowApplication workflowApp;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager em;

    @BeforeEach
    void clearListener() {
        AgenticListener.clearAll();
    }

    @AfterEach
    @Transactional
    void cleanupDatabase() {
        em.createNativeQuery("DELETE FROM task_info_entity").executeUpdate();
        em.createNativeQuery("DELETE FROM cloud_event_entity").executeUpdate();
        em.createNativeQuery("DELETE FROM workflow_instance_entity").executeUpdate();
    }

    @Test
    void should_execute_through_event_trigger() {
        EventPublisher publisher = workflowApp.eventPublishers().iterator().next();

        publishCloudEvent(publisher, Map.of(
                "id", 1L,
                "title", "Quarkus Flow, Java and IA Orchestration",
                "description", "It is a great talk (trust)",
                "subject", "Quarkus Flow, Java and IA Orchestration"));

        Awaitility.await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    Assertions.assertThat(AgenticListener.WORKFLOW_COMPLETED_EVENTS)
                            .filteredOn(event -> event.workflowContext().definition().id().name()
                                    .equals("conference-reviewer-planner-schedulable"))
                            .isNotEmpty();
                });
    }

    private void publishCloudEvent(EventPublisher publisher, Map<String, Object> data) {
        JsonNode root = objectMapper.convertValue(data, JsonNode.class);
        publisher.publish(new CloudEventBuilder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("http://localhost/quarkus-flow"))
                .withType("proposal.submitted")
                .withData(JsonCloudEventData.wrap(root))
                .build());
    }
}
