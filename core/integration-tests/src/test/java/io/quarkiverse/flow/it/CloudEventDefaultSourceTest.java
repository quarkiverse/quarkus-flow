package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Verifies the end-to-end wiring of {@code quarkus.flow.cloud-events}: when an emit task declares no source, the
 * registrar injects a default source derived from the workflow identity.
 */
@QuarkusTest
class CloudEventDefaultSourceTest {

    @Inject
    OrderEmitterWorkflow orderEmitter;

    @Test
    @DisplayName("emit_without_source_gets_identity_derived_default")
    void emit_without_source_gets_identity_derived_default() {
        WorkflowDefinitionId id = orderEmitter.definition().id();
        String expectedSource = id.toString(":");

        Task task = orderEmitter.definition().workflow().getDo().get(0).getTask();
        EmitTask emit = task.getEmitTask();
        assertThat(emit).as("first task should be an emit").isNotNull();

        EventProperties props = emit.getEmit().getEvent().getWith();
        assertThat(props.getSource()).as("source should have been injected").isNotNull();

        UriTemplate template = props.getSource().getUriTemplate();
        String actual = template.getLiteralUri() != null
                ? template.getLiteralUri().toString()
                : template.getLiteralUriTemplate();
        assertThat(actual).isEqualTo(expectedSource);
        assertThat(actual).isEqualTo(OrderEmitterWorkflow.NAMESPACE + ":" + OrderEmitterWorkflow.NAME + ":"
                + id.version());
    }
}
