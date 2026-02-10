package io.quarkiverse.flow.internal;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class WorkflowNameUtilsTest {

    /**
     * Test Data provider method.
     */
    private static Stream<Arguments> provideSanitizationData() {
        return Stream.of(
                // Validate handling of names in basic camelCase
                arguments("MySimpleWorkflow", "my-simple-workflow"),
                // Validate handling of names with numbers
                arguments("WorkflowWith123Numbers", "workflow-with123-numbers"),
                // Validate handling of names already in kebab case
                arguments("Already-Kebab-Case", "already-kebab-case"),
                // Validate handling of names starting with digits
                arguments("123MyWorkflow", "123-my-workflow"),
                // Validate handling of names that are acronyms
                arguments("XMLProcessor", "xml-processor"),
                // Validate handling of names with special characters
                arguments("Symbols!@#$%^&*", "symbols"),
                // Validate handling of names with leading and trailing dashes
                arguments("---leading-trailing---", "leading-trailing"),
                // Validate handling of names with spaces
                arguments("  spaces  ", "spaces"));
    }

    @ParameterizedTest(name = "{index} => input=''{0}'', expected=''{1}''")
    @MethodSource("provideSanitizationData")
    @DisplayName("Should correctly sanitize names")
    void testSafeNameSanitization(String input, String expected) {
        assertThat(WorkflowNameUtils.safeName(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should truncate names longer than 63 characters")
    void testLongNameTruncation() {
        String longInput = "ThisIsAReallyLongWorkflowNameThatDefinitelyExceedsTheSixtyThreeCharacterLimit";
        String result = WorkflowNameUtils.safeName(longInput);

        assertThat(result).hasSizeLessThanOrEqualTo(63);
        assertThat(result).doesNotEndWith("-"); // Ensure trailing dash from truncation is stripped
    }

    @Test
    @DisplayName("Should throw exception for invalid null or blank inputs")
    void testInvalidInputs() {
        assertThatThrownBy(() -> WorkflowNameUtils.safeName(null, null))
                .isInstanceOf(IllegalArgumentException.class); //

        assertThatThrownBy(() -> WorkflowNameUtils.safeName("   ", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
