package io.quarkiverse.flow.langchain4j.it;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
public class SchedulableFlowsIT {

    @Inject
    WorkflowApplication workflowApp;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void clearListener() {
        AgenticListener.clearAll();
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

    @Test
    void should_execute_agent_using_every_trigger() {

        // The MessageSummaryAgentic is called by the workflow engine dynamically
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {

            Assertions.assertThat(AgenticListener.WORKFLOW_STARTED_EVENTS).filteredOn(
                    e -> e.workflowContext().definition().id().name().equals("email-summary-agentic")).isNotEmpty();

        });

    }

    @Test
    void should_execute_workflow_using_cron_trigger() {
        // 1m and 5s
        Awaitility.await().atMost(Duration.ofSeconds(65)).untilAsserted(() -> {
            Assertions.assertThat(AgenticListener.WORKFLOW_STARTED_EVENTS).filteredOn(
                    e -> e.workflowContext().definition().id().name().equals("whats-app-summary-agentic")).isNotEmpty();
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
