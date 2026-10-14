package io.quarkiverse.flow.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.internal.NoOpSchemaValidatorFactory;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;

/**
 * Verifies the mechanism behind {@code quarkus.flow.validation.input.enabled}: with a
 * {@link NoOpSchemaValidatorFactory} installed (the default, flag disabled), non-conforming input is
 * silently accepted; with the real {@code serverlessworkflow-impl-validation} validator in play (flag
 * enabled, nothing overriding the SDK's own {@link java.util.ServiceLoader} discovery), non-conforming
 * input is rejected.
 */
class InputSchemaValidationTest {

    private static final Map<String, Object> REQUIRES_NAME_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("name", Map.of("type", "string")),
            "required", List.of("name"));

    private static Workflow workflowRequiringName() {
        return FlowWorkflowBuilder.workflow("test-input-schema-validation")
                .input(input -> input.schema(REQUIRES_NAME_SCHEMA))
                .tasks(doTask -> doTask.set("greet", setBlock -> setBlock.expr(Map.of("greeted", true))))
                .build();
    }

    @Test
    @DisplayName("missing required input field is silently accepted when validation is disabled (default)")
    void missing_required_field_accepted_when_validation_disabled() throws IOException {
        try (WorkflowApplication app = WorkflowApplication.builder()
                .withSchemaValidatorFactory(new NoOpSchemaValidatorFactory())
                .build()) {

            WorkflowInstance instance = app
                    .workflowDefinition(TestSerializationUtils.writeAndReadInMemory(workflowRequiringName()))
                    .instance(Map.of());

            CompletableFuture<WorkflowModel> future = instance.start();

            assertThat(future.join()).isNotNull();
        }
    }

    @Test
    @DisplayName("missing required input field is rejected when validation is enabled")
    void missing_required_field_rejected_when_validation_enabled() throws IOException {
        try (WorkflowApplication app = WorkflowApplication.builder().build()) {

            Workflow workflow = TestSerializationUtils.writeAndReadInMemory(workflowRequiringName());

            assertThatThrownBy(() -> app.workflowDefinition(workflow).instance(Map.of()))
                    .isInstanceOf(WorkflowException.class)
                    .hasMessageContaining("required property 'name' not found");
        }
    }

}
