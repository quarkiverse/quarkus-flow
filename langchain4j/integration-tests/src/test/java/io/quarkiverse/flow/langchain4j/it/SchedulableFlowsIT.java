package io.quarkiverse.flow.langchain4j.it;

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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.core.v1.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.EventPublisher;

@QuarkusTest
@QuarkusTestResource(value = FlowScheduleOllamaMockResource.class, restrictToAnnotatedClass = true)
@TestMethodOrder(OrderAnnotation.class)
public class SchedulableFlowsIT {

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
        // Delete in correct order: children first, then parents
        em.createNativeQuery("DELETE FROM task_info_entity").executeUpdate();
        em.createNativeQuery("DELETE FROM cloud_event_entity").executeUpdate();
        em.createNativeQuery("DELETE FROM workflow_instance_entity").executeUpdate();
    }

    @Test
    @Order(1)
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

    @Test
    @Order(2)
    void should_execute_agent_using_every_trigger() {
        // Capture current count to detect NEW events after test starts
        int initialCount = (int) AgenticListener.WORKFLOW_STARTED_EVENTS.stream()
                .filter(e -> e.workflowContext().definition().id().name().equals("email-summary-agentic"))
                .count();

        // The MessageSummaryAgentic is called by the workflow engine dynamically (every 3 seconds)
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            long currentCount = AgenticListener.WORKFLOW_STARTED_EVENTS.stream()
                    .filter(e -> e.workflowContext().definition().id().name().equals("email-summary-agentic"))
                    .count();

            Assertions.assertThat(currentCount)
                    .as("New email-summary-agentic workflow should have started")
                    .isGreaterThan(initialCount);
        });
    }

    @Test
    @Order(3)
    void should_execute_workflow_using_cron_trigger() {
        // Capture current count to detect NEW events after test starts
        int initialCount = (int) AgenticListener.WORKFLOW_STARTED_EVENTS.stream()
                .filter(e -> e.workflowContext().definition().id().name().equals("whats-app-summary-agentic"))
                .count();

        // 1m and 5s cron schedule
        Awaitility.await().atMost(Duration.ofSeconds(65)).untilAsserted(() -> {
            long currentCount = AgenticListener.WORKFLOW_STARTED_EVENTS.stream()
                    .filter(e -> e.workflowContext().definition().id().name().equals("whats-app-summary-agentic"))
                    .count();

            Assertions.assertThat(currentCount)
                    .as("New whats-app-summary-agentic workflow should have started")
                    .isGreaterThan(initialCount);
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
