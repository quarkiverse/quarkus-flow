package io.quarkiverse.flow.dsl;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowWorkflowBuilderValidationTest {

    @Test
    @DisplayName("test_build_rejects_namespace_with_invalid_characters")
    void test_build_rejects_namespace_with_invalid_characters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FlowWorkflowBuilder.workflow("processPhotoWorkflow", "guru.quarkus").build())
                .withMessageContaining("document.namespace: must match \"^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$");
    }

    @Test
    @DisplayName("test_build_rejects_name_with_invalid_characters")
    void test_build_rejects_name_with_invalid_characters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FlowWorkflowBuilder.workflow("bad name!", "guru-quarkus").build())
                .withMessageContaining("document.name");
    }

    @Test
    @DisplayName("test_build_rejects_non_semver_version")
    void test_build_rejects_non_semver_version() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FlowWorkflowBuilder.workflow("processPhotoWorkflow", "guru-quarkus", "v1").build())
                .withMessageContaining("document.version");
    }

    @Test
    @DisplayName("test_build_does_not_validate_nested_task_fields")
    void test_build_does_not_validate_nested_task_fields() {
        // CallHTTP.with is left completely empty here: both "method" and "endpoint"
        // are @NotNull on HTTPArguments (and required by the JSON Schema), but the
        // cascade never reaches into task content, so build() succeeds anyway.
        assertThatNoException()
                .isThrownBy(() -> FlowWorkflowBuilder.workflow("processPhotoWorkflow", "guru-quarkus")
                        .tasks(doTask -> doTask.http("callTask", httpTask -> {
                        }))
                        .build());
    }
}
