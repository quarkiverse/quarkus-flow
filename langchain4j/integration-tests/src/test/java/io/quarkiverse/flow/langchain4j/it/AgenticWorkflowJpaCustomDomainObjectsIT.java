package io.quarkiverse.flow.langchain4j.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import io.quarkiverse.flow.langchain4j.spec.AgenticAwareModelFactory;
import io.quarkiverse.flow.persistence.jpa.WorkflowModelConverter;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Reproduces issue #901: AgenticAwareWorkflowModelDeserializer fails to deserialize
 * application domain classes due to classloader mismatch in JacksonAgenticScopeJsonCodec.
 *
 * <p>
 * When quarkus-flow-jpa is used to persist workflow instances, any workflow that stores
 * application domain objects in the AgenticScope fails with a PersistenceException wrapping
 * an UnserializableAgenticScopeException. The root cause is that JacksonAgenticScopeJsonCodec
 * holds a static final ObjectMapper built with the parent classloader, which cannot see
 * application classes loaded by the Quarkus runtime classloader.
 * </p>
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/901">Issue #901</a>
 */
@QuarkusTest
@QuarkusTestResource(value = FlowAgentOllamaMockResource.class, restrictToAnnotatedClass = true)
public class AgenticWorkflowJpaCustomDomainObjectsIT {

    @Inject
    Agents.ExpertRouterAgent expertRouterAgent;

    @Inject
    WorkflowModelConverter converter;

    /**
     * Custom domain object representing a trip itinerary.
     * This simulates real-world application domain objects that users would store in AgenticScope.
     */
    public static class TripItinerary {
        private String destination;
        private int durationDays;
        private List<Activity> activities;
        private CostEstimate cost;

        public TripItinerary() {
        }

        public TripItinerary(String destination, int durationDays, List<Activity> activities, CostEstimate cost) {
            this.destination = destination;
            this.durationDays = durationDays;
            this.activities = activities;
            this.cost = cost;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public int getDurationDays() {
            return durationDays;
        }

        public void setDurationDays(int durationDays) {
            this.durationDays = durationDays;
        }

        public List<Activity> getActivities() {
            return activities;
        }

        public void setActivities(List<Activity> activities) {
            this.activities = activities;
        }

        public CostEstimate getCost() {
            return cost;
        }

        public void setCost(CostEstimate cost) {
            this.cost = cost;
        }
    }

    public static class Activity {
        private String name;
        private String time;

        public Activity() {
        }

        public Activity(String name, String time) {
            this.name = name;
            this.time = time;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }
    }

    public static class CostEstimate {
        private double totalCost;
        private String currency;

        public CostEstimate() {
        }

        public CostEstimate(double totalCost, String currency) {
            this.totalCost = totalCost;
            this.currency = currency;
        }

        public double getTotalCost() {
            return totalCost;
        }

        public void setTotalCost(double totalCost) {
            this.totalCost = totalCost;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }

    @Test
    @DisplayName("agentic_workflow_with_custom_domain_objects_succeeds_jpa_persistence_issue_901_fixed")
    void agentic_workflow_with_custom_domain_objects_succeeds_jpa_persistence_issue_901_fixed() {
        // Arrange: Execute an agent to get a real AgenticScope, then add custom domain objects
        ResultWithAgenticScope<String> result = expertRouterAgent
                .ask("I have severe chest pain and difficulty breathing, what medical treatment should I seek?");
        AgenticScope scope = result.agenticScope();

        // Add custom application domain objects to the scope
        TripItinerary itinerary = new TripItinerary(
                "Paris",
                5,
                List.of(
                        new Activity("Visit Eiffel Tower", "10:00 AM"),
                        new Activity("Louvre Museum", "2:00 PM")),
                new CostEstimate(1500.00, "USD"));

        scope.writeState("itinerary", itinerary);
        scope.writeState("userPreference", "luxury");

        WorkflowModel model = new AgenticAwareModelFactory().fromOther(scope);

        // Act: Persist and restore should now succeed with FlowJacksonAgenticScopeJsonCodec
        byte[] persisted = converter.convertToDatabaseColumn(model);
        assertThat(persisted).as("Serialization should succeed").isNotEmpty();

        WorkflowModel restored = converter.convertToEntityAttribute(persisted);

        // Assert: Restored model should preserve custom domain objects
        assertThat(restored.asMap()).isPresent();
        Map<String, Object> restoredState = restored.asMap().orElseThrow();

        // Verify custom domain objects are preserved
        assertThat(restoredState).containsKey("itinerary");
        assertThat(restoredState).containsKey("userPreference");
        assertThat(restoredState.get("userPreference")).isEqualTo("luxury");
    }

    @Test
    @DisplayName("agentic_workflow_with_nested_custom_objects_succeeds_jpa_persistence_issue_901_fixed")
    void agentic_workflow_with_nested_custom_objects_succeeds_jpa_persistence_issue_901_fixed() {
        // Arrange: Get a real scope and add nested custom objects (similar to TripPlan$CostEstimate in bug report)
        ResultWithAgenticScope<String> result = expertRouterAgent
                .ask("I have severe chest pain and difficulty breathing, what medical treatment should I seek?");
        AgenticScope scope = result.agenticScope();

        CostEstimate nestedCost = new CostEstimate(2500.00, "EUR");
        scope.writeState("costs", nestedCost);

        WorkflowModel model = new AgenticAwareModelFactory().fromOther(scope);

        // Act: Nested classes should now also succeed
        byte[] persisted = converter.convertToDatabaseColumn(model);
        assertThat(persisted).isNotEmpty();

        WorkflowModel restored = converter.convertToEntityAttribute(persisted);

        // Assert
        assertThat(restored.asMap()).isPresent();
        assertThat(restored.asMap().orElseThrow()).containsKey("costs");
    }

    @Test
    @DisplayName("agentic_workflow_with_jdk_types_succeeds_jpa_persistence")
    void agentic_workflow_with_jdk_types_succeeds_jpa_persistence() {
        // Arrange: This is the current test coverage - only JDK types and enums work
        ResultWithAgenticScope<String> result = expertRouterAgent
                .ask("I have severe chest pain and difficulty breathing, what medical treatment should I seek?");
        AgenticScope scope = result.agenticScope();

        // Add additional JDK types
        scope.writeState("count", 42);
        scope.writeState("message", "test");

        WorkflowModel model = new AgenticAwareModelFactory().fromOther(scope);

        // Act: Round-trip should succeed for JDK types
        byte[] persisted = converter.convertToDatabaseColumn(model);
        assertThat(persisted).isNotEmpty();

        WorkflowModel restored = converter.convertToEntityAttribute(persisted);

        // Assert
        assertThat(restored.asMap()).isPresent();
        assertThat(restored.asMap().orElseThrow())
                .containsEntry("category", Agents.RequestCategory.MEDICAL)
                .containsEntry("count", 42)
                .containsEntry("message", "test");
    }
}
