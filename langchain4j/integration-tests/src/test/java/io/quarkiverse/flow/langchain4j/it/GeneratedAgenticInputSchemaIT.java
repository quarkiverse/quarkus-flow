package io.quarkiverse.flow.langchain4j.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end coverage for #1111: the input schema derived from an agent method signature must permit everything
 * LangChain4j actually puts into the {@code AgenticScope} state map - an all-null record and an
 * {@code InvocationParameters} that never reaches the state map at all - and an enum that Jackson writes through
 * {@code @JsonValue}.
 */
@QuarkusTest
@QuarkusTestResource(value = FlowAgentOllamaMockResource.class, restrictToAnnotatedClass = true)
public class GeneratedAgenticInputSchemaIT {

    @Inject
    ExtractionService extractionService;

    @Test
    @DisplayName("test_all_null_record_and_invocation_parameters_pass_input_validation")
    void test_all_null_record_and_invocation_parameters_pass_input_validation() {
        assertThat(extractionService).isNotNull();

        // This used to be rejected upfront with "null found, string expected" and
        // "required property 'invocationParameters' not found"
        assertThatCode(() -> extractionService.extract("hello there")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_extraction_runs_and_returns_the_extracted_fields")
    void test_extraction_runs_and_returns_the_extracted_fields() {
        Agents.ExtractedFields extracted = extractionService.extract("hello there");

        assertThat(extracted)
                .isNotNull()
                .extracting(Agents.ExtractedFields::category, Agents.ExtractedFields::location)
                .containsExactly("DEMO-CATEGORY", "DEMO-LOCATION");
    }

    @Test
    @DisplayName("test_json_value_enum_in_a_record_and_a_list_passes_input_validation")
    void test_json_value_enum_in_a_record_and_a_list_passes_input_validation() {
        // This used to be rejected upfront with "does not have a value in the enumeration [\"HIGH\", \"LOW\", null]"
        assertThat(extractionService.prioritize("hello there"))
                .extracting(Agents.PrioritizedFields::priority)
                .isEqualTo(Agents.Priority.HIGH);
    }
}
