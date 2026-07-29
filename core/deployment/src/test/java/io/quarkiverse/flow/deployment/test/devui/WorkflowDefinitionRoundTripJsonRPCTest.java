package io.quarkiverse.flow.deployment.test.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.QuarkusDevModeTest;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class WorkflowDefinitionRoundTripJsonRPCTest extends FlowDevUITestBase {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(EchoNameWorkflow.class));

    private static final WorkflowDefinitionId ECHO_ID = WorkflowDefinitionId.of(new EchoNameWorkflow().descriptor());

    public WorkflowDefinitionRoundTripJsonRPCTest() {
        super("quarkus-flow", "http://localhost:8080");
    }

    @Test
    @DisplayName("getWorkflowDefinition is idempotent — two calls return identical JSON")
    void getWorkflowDefinitionIsIdempotent() throws Exception {
        JsonNode first = executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", ECHO_ID));
        JsonNode second = executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", ECHO_ID));

        assertThat(first.asText())
                .as("second call must return the same JSON as the first")
                .isEqualTo(second.asText());
    }

    @Test
    @DisplayName("getWorkflowDefinition returns a semantically equivalent Workflow")
    void getWorkflowDefinitionIsSemanticallyEquivalent() throws Exception {
        String json = executeJsonRPCMethod("getWorkflowDefinition",
                Map.of("id", ECHO_ID)).asText();

        Workflow roundTripped = WorkflowFormat.JSON.mapper().readValue(json, Workflow.class);

        assertThat(roundTripped.getDocument().getName()).isEqualTo("echo-name");
        assertThat(roundTripped.getDocument().getNamespace()).isEqualTo("flow");
        assertThat(roundTripped.getDocument().getVersion()).isEqualTo("0.1.0");
        assertThat(roundTripped.getDo()).hasSize(1);
        assertThat(roundTripped.getDo().get(0).getName()).isEqualTo("setEcho");
    }

    @Test
    @DisplayName("repeated getWorkflowDefinition calls do not grow the workflow registry")
    void repeatedCallsDoNotMutateRegistry() throws Exception {
        int before = executeJsonRPCMethod("getNumbersOfWorkflows").asInt();

        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", ECHO_ID));
        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", ECHO_ID));
        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", ECHO_ID));

        int after = executeJsonRPCMethod("getNumbersOfWorkflows").asInt();

        assertThat(after)
                .as("registry size must not change after repeated getWorkflowDefinition calls")
                .isEqualTo(before);
    }
}
