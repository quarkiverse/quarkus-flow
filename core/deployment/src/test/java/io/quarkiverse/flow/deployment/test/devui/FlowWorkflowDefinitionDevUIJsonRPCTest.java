package io.quarkiverse.flow.deployment.test.devui;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

public class FlowWorkflowDefinitionDevUIJsonRPCTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HelloResource.class, HelloWorkflow.class));

    public FlowWorkflowDefinitionDevUIJsonRPCTest() {
        super("quarkus-flow");
    }

    @Test
    void shouldHaveOneWorkflow() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("getNumbersOfWorkflows");
        Assertions.assertEquals(1, node.asInt());
    }

    @Test
    void shouldGenerateMermaidDefinition() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("generateMermaid", Map.of("name", "helloWorld"));
        Assertions.assertTrue(node.get("mermaid").asText().contains("flowchart TD"));
    }

    @Test
    void shouldGetWorkflowInfo() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("getWorkflows");
        Assertions.assertEquals("helloWorld", node.get(0).get("name").asText());
    }

}
