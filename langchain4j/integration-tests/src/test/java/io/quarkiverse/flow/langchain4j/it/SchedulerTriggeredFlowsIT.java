package io.quarkiverse.flow.langchain4j.it;

import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.startOllamaMock;
import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.stubEmailSummaryAgent;
import static io.quarkiverse.flow.langchain4j.it.WiremockOllamaUtils.stubWhatsAppSummaryAgent;

import java.time.Duration;
import java.util.Map;

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

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = SchedulerTriggeredFlowsIT.OllamaMockResource.class, restrictToAnnotatedClass = true)
@TestMethodOrder(OrderAnnotation.class)
public class SchedulerTriggeredFlowsIT {

    public static class OllamaMockResource implements QuarkusTestResourceLifecycleManager {

        private WireMockServer wireMock;

        @Override
        public Map<String, String> start() {
            wireMock = startOllamaMock();
            stubEmailSummaryAgent(wireMock);
            stubWhatsAppSummaryAgent(wireMock);
            return Map.of(
                    "quarkus.langchain4j.ollama.base-url", wireMock.baseUrl(),
                    "quarkus.scheduler.enabled", "true");
        }

        @Override
        public void stop() {
            if (wireMock != null) {
                wireMock.stop();
            }
        }
    }

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
    @Order(1)
    void should_execute_agent_using_every_trigger() {
        int initialCount = (int) AgenticListener.WORKFLOW_STARTED_EVENTS.stream()
                .filter(e -> e.workflowContext().definition().id().name().equals("email-summary-agentic"))
                .count();

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
    @Order(2)
    void should_execute_workflow_using_cron_trigger() {
        int initialCount = (int) AgenticListener.WORKFLOW_STARTED_EVENTS.stream()
                .filter(e -> e.workflowContext().definition().id().name().equals("whats-app-summary-agentic"))
                .count();

        Awaitility.await().atMost(Duration.ofSeconds(65)).untilAsserted(() -> {
            long currentCount = AgenticListener.WORKFLOW_STARTED_EVENTS.stream()
                    .filter(e -> e.workflowContext().definition().id().name().equals("whats-app-summary-agentic"))
                    .count();

            Assertions.assertThat(currentCount)
                    .as("New whats-app-summary-agentic workflow should have started")
                    .isGreaterThan(initialCount);
        });
    }
}
