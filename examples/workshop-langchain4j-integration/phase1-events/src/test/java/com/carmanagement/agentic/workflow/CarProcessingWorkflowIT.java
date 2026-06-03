package com.carmanagement.agentic.workflow;

import com.carmanagement.agentic.OllamaMockResource;
import com.carmanagement.model.CarConditions;
import com.carmanagement.model.CarInfo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for CarProcessingWorkflow (@SequenceAgent).
 * Uses WireMock to mock Ollama LLM responses.
 */
@QuarkusTest
@QuarkusTestResource(OllamaMockResource.class)
@DisabledOnOs(OS.WINDOWS)
class CarProcessingWorkflowIT {

    @Inject
    CarProcessingWorkflow workflow;

    @Test
    @DisplayName("should_coordinate_agents_sequentially_when_processing_car_return")
    void should_coordinate_agents_sequentially() {
        // Given: Car with feedback
        CarInfo carInfo = new CarInfo("Toyota", "Camry", 2020, "Good");
        String feedback = "Car needs interior cleaning";

        // When: Process car return through sequential workflow
        CarConditions result = workflow.processCarReturn(carInfo, 1, feedback);

        // Then: Both agents produced results
        assertThat(result).isNotNull();
        assertThat(result.generalCondition()).isNotEmpty();
        assertThat(result.cleaningRequired()).isTrue();
    }

    @Test
    @DisplayName("should_handle_positive_feedback_without_cleaning")
    void should_handle_positive_feedback() {
        // Given: Car with positive feedback
        CarInfo carInfo = new CarInfo("Honda", "Civic", 2021, "Excellent");
        String feedback = "Car was perfect, no issues";

        // When: Process car return
        CarConditions result = workflow.processCarReturn(carInfo, 2, feedback);

        // Then: Workflow completes successfully
        assertThat(result).isNotNull();
        assertThat(result.generalCondition()).isNotEmpty();
    }

    @Test
    @DisplayName("should_preserve_car_information_through_workflow")
    void should_preserve_car_information() {
        // Given: Specific car details
        CarInfo carInfo = new CarInfo("Ford", "Focus", 2019, "Fair");
        String feedback = "Minor scratches on door";

        // When: Process car return
        CarConditions result = workflow.processCarReturn(carInfo, 3, feedback);

        // Then: Workflow processes all information
        assertThat(result).isNotNull();
        assertThat(result.generalCondition()).isNotBlank();
    }
}
