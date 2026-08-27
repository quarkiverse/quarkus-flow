package io.quarkiverse.flow.langchain4j.it;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Verifies that an agentic workflow model survives the exact serialization path the JPA persistence
 * layer uses: {@link WorkflowModelConverter} marshals the model to bytes and back. This exercises
 * {@code AgenticAwareWorkflowModelMarshaller}, which serializes the {@link AgenticScope} through the
 * managed {@code ObjectMapper}. A round-trip keeps the assertion deterministic and free of the
 * workflow-completion lifecycle (completed instances are not retained, so counting rows is meaningless).
 */
@QuarkusTest
@QuarkusTestResource(value = FlowAgentOllamaMockResource.class, restrictToAnnotatedClass = true)
public class AgenticWorkflowWithJpaPersistenceIT {

    @Inject
    Agents.ExpertRouterAgent expertRouterAgent;

    @Inject
    WorkflowModelConverter converter;

    @Test
    @DisplayName("agentic_workflow_model_survives_jpa_persistence_round_trip")
    void agentic_workflow_model_survives_jpa_persistence_round_trip() {
        ResultWithAgenticScope<String> result = expertRouterAgent
                .ask("I have severe chest pain and difficulty breathing, what medical treatment should I seek?");
        AgenticScope scope = result.agenticScope();
        assertThat(scope.readState("category"))
                .as("sanity: the agentic scope carries the routed category before persistence")
                .isEqualTo(Agents.RequestCategory.MEDICAL);

        WorkflowModel model = new AgenticAwareModelFactory().fromOther(scope);

        byte[] persisted = converter.convertToDatabaseColumn(model);
        assertThat(persisted)
                .as("the AgenticScope should marshal to bytes instead of failing silently")
                .isNotEmpty();

        WorkflowModel restored = converter.convertToEntityAttribute(persisted);

        assertThat(restored.asMap())
                .as("restored agentic model should expose its state")
                .isPresent();

        // Verify state is preserved (note: enums may be serialized/deserialized differently due to polymorphic typing)
        Map<String, Object> restoredState = restored.asMap().orElseThrow();
        assertThat(restoredState)
                .as("restored agentic state should preserve the original scope state")
                .containsKeys(scope.state().keySet().toArray(new String[0]));

        // Check category value - may be enum or string representation depending on serialization
        Object category = restoredState.get("category");
        assertThat(category)
                .as("category should be preserved")
                .satisfiesAnyOf(
                        c -> assertThat(c).isEqualTo(Agents.RequestCategory.MEDICAL),
                        c -> assertThat(c.toString()).isEqualTo("MEDICAL"));
    }
}
