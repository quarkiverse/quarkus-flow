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
 * Verifies issue #901 fix: AgenticScope with custom domain objects now deserializes correctly.
 * <p>
 * quarkus-langchain4j (since 1.13.3) automatically registers domain types that appear in agentic
 * method signatures, allowing them to be deserialized from AgenticScope after JPA persistence.
 * <p>
 * {@link TripPlannerAgent} exists solely to trigger automatic type registration for
 * {@link TripItinerary}, {@link Activity}, and {@link CostEstimate}. The agent is not invoked in
 * these tests (to avoid CI overhead), but its presence causes quarkus-langchain4j to register
 * the types at build time, fixing the classloader/allowlist issue from #901.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/901">Issue #901</a>
 * @see <a href="https://github.com/quarkiverse/quarkus-langchain4j/pull/2869">quarkus-langchain4j PR #2869</a>
 */
@QuarkusTest
@QuarkusTestResource(value = FlowAgentOllamaMockResource.class, restrictToAnnotatedClass = true)
public class AgenticWorkflowJpaCustomDomainObjectsIT {

    @Inject
    Agents.ExpertRouterAgent expertRouterAgent;
    @Inject
    WorkflowModelConverter converter;

    @Test
    @DisplayName("agentic_workflow_with_custom_domain_objects_succeeds_jpa_persistence_issue_901_fixed")
    void agentic_workflow_with_custom_domain_objects_succeeds_jpa_persistence_issue_901_fixed() {
        // Arrange: Execute an agent to get a real AgenticScope, then add custom domain objects
        // Note: TripPlannerAgent's method signature causes quarkus-langchain4j to auto-register
        // TripItinerary, Activity, and CostEstimate for deserialization
        ResultWithAgenticScope<String> result = expertRouterAgent
                .ask("I have severe chest pain and difficulty breathing, what medical treatment should I seek?");
        AgenticScope scope = result.agenticScope();

        // Add custom application domain objects to the scope
        TripPlannerAgent.TripItinerary itinerary = new TripPlannerAgent.TripItinerary(
                "Paris",
                5,
                List.of(
                        new TripPlannerAgent.Activity("Visit Eiffel Tower", "10:00 AM"),
                        new TripPlannerAgent.Activity("Louvre Museum", "2:00 PM")),
                new TripPlannerAgent.CostEstimate(1500.00, "USD"));

        scope.writeState("itinerary", itinerary);
        scope.writeState("userPreference", "luxury");

        WorkflowModel model = new AgenticAwareModelFactory().fromOther(scope);

        // Act: Persist and restore should now succeed (types auto-registered via TripPlannerAgent)
        byte[] persisted = converter.convertToDatabaseColumn(model);
        assertThat(persisted).as("Serialization should succeed").isNotEmpty();

        WorkflowModel restored = converter.convertToEntityAttribute(persisted);

        // Assert: Restored model should preserve custom domain objects
        assertThat(restored.asMap()).isPresent();
        Map<String, Object> restoredState = restored.asMap().orElseThrow();

        // Verify custom domain objects are preserved
        assertThat(restoredState.get("itinerary")).isInstanceOf(TripPlannerAgent.TripItinerary.class);

        TripPlannerAgent.TripItinerary restoredItinerary = (TripPlannerAgent.TripItinerary) restoredState.get("itinerary");
        assertThat(restoredItinerary.destination()).isEqualTo("Paris");
        assertThat(restoredItinerary.durationDays()).isEqualTo(5);
        assertThat(restoredItinerary.activities()).hasSize(2);
        assertThat(restoredItinerary.cost().currency()).isEqualTo("USD");

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

        TripPlannerAgent.CostEstimate nestedCost = new TripPlannerAgent.CostEstimate(2500.00, "EUR");
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
        // Arrange: JDK types don't require registration
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
        Map<String, Object> restoredState = restored.asMap().orElseThrow();

        assertThat(restoredState)
                .containsEntry("count", 42)
                .containsEntry("message", "test");

        // Enum comparison by name to handle classloader differences
        Object categoryValue = restoredState.get("category");
        assertThat(categoryValue).isNotNull();
        assertThat(((Enum<?>) categoryValue).name())
                .isEqualTo(Agents.RequestCategory.MEDICAL.name());
    }
}
