package io.quarkiverse.flow.deployment.test.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.QuarkusDevModeTest;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class WorkflowDefinitionYamlRoundTripJsonRPCTest extends FlowDevUITestBase {

    // Live reference to the canonical docs YAML — not a copy.
    // Any breaking change to docs/modules/ROOT/examples/flow/echo-name.yaml
    // will immediately fail this test. The path is relative to the Maven module
    // root (core/deployment/), which is the working directory at test runtime.
    static final File ECHO_NAME_YAML = new File(
            "../../docs/modules/ROOT/examples/flow/echo-name.yaml");

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.flow.definitions.dir=flow\n"),
                            "application.properties")
                    .addAsResource(new FileAsset(ECHO_NAME_YAML), "flow/echo-name.yaml"));

    // namespace=company, name=echo-name, version=0.1.0 — as declared in the YAML
    private static final WorkflowDefinitionId YAML_ECHO_ID = new WorkflowDefinitionId("company", "echo-name", "0.1.0");

    public WorkflowDefinitionYamlRoundTripJsonRPCTest() {
        super("quarkus-flow", "http://localhost:8080");
    }

    @Test
    @DisplayName("getWorkflowDefinition is idempotent for a YAML-loaded workflow")
    void getYamlWorkflowDefinitionIsIdempotent() throws Exception {
        JsonNode first = executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", YAML_ECHO_ID));
        JsonNode second = executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", YAML_ECHO_ID));

        assertThat(first.asText())
                .as("second call must return the same JSON as the first")
                .isEqualTo(second.asText());
    }

    @Test
    @DisplayName("getWorkflowDefinition returns a semantically equivalent Workflow for a YAML source")
    void getYamlWorkflowDefinitionIsSemanticallyEquivalent() throws Exception {
        String json = executeJsonRPCMethod("getWorkflowDefinition",
                Map.of("id", YAML_ECHO_ID)).asText();

        Workflow roundTripped = WorkflowFormat.JSON.mapper().readValue(json, Workflow.class);

        assertThat(roundTripped.getDocument().getName()).isEqualTo("echo-name");
        assertThat(roundTripped.getDocument().getNamespace()).isEqualTo("company");
        assertThat(roundTripped.getDocument().getVersion()).isEqualTo("0.1.0");
        assertThat(roundTripped.getDo()).hasSize(1);
        assertThat(roundTripped.getDo().get(0).getName()).isEqualTo("setEcho");
    }

    @Test
    @DisplayName("repeated getWorkflowDefinition calls do not grow the registry for a YAML-loaded workflow")
    void repeatedYamlCallsDoNotMutateRegistry() throws Exception {
        int before = executeJsonRPCMethod("getNumbersOfWorkflows").asInt();

        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", YAML_ECHO_ID));
        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", YAML_ECHO_ID));
        executeJsonRPCMethod("getWorkflowDefinition", Map.of("id", YAML_ECHO_ID));

        int after = executeJsonRPCMethod("getNumbersOfWorkflows").asInt();

        assertThat(after)
                .as("registry size must not change after repeated getWorkflowDefinition calls")
                .isEqualTo(before);
    }
}
