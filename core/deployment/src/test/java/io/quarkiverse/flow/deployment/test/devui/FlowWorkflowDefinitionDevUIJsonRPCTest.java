package io.quarkiverse.flow.deployment.test.devui;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

public class FlowWorkflowDefinitionDevUIJsonRPCTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.http.port=0"), "application.properties")
                    .addClasses(GreetingResource.class, DevUIWorkflow.class));

    private static final WorkflowDefinitionId workflowId = WorkflowDefinitionId.of(new DevUIWorkflow().descriptor());

    public FlowWorkflowDefinitionDevUIJsonRPCTest() {
        super("quarkus-flow", "http://localhost:8080");
    }

    @Test
    public void shouldHaveOneWorkflow() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("getNumbersOfWorkflows");
        Assertions.assertEquals(1, node.asInt());
    }

    @Test
    public void shouldGenerateMermaidDiagram() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("generateMermaidDiagram", Map.of("id", workflowId));
        Assertions.assertTrue(node.get("mermaid").asText().contains("flowchart TD"));
    }

    @Test
    public void shouldGetWorkflowInfo() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("getWorkflows");
        Assertions.assertEquals("helloQuarkus", node.get(0).get("id").get("name").asText());
    }

    @Test
    public void shouldExecuteWorkflow() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("executeWorkflow", Map.of(
                "id", workflowId));

        Assertions.assertEquals("application/json", node.get("mimetype").asText());
        Assertions.assertTrue(node.get("data").has("message"));
    }

}
