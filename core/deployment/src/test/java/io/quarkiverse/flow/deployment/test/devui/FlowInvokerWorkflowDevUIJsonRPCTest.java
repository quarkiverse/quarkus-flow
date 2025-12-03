package io.quarkiverse.flow.deployment.test.devui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

public class FlowInvokerWorkflowDevUIJsonRPCTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            AgenticDevUIWorkflow.class,
                            DevUIAgenticServiceBean.class));

    public FlowInvokerWorkflowDevUIJsonRPCTest() {
        super("quarkus-flow");
    }

    @Test
    void shouldExecuteAgenticWorkflowViaBeanInvoker() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("executeWorkflow", Map.of(
                "workflowName", "agenticDevUI",
                "input", """
                        {
                          "var1": "topic-value",
                          "var2": 42,
                          "var3": true
                        }
                        """));

        // Because the bean returns a plain String, WorkflowRPCService sets text/plain
        assertEquals("text/plain", node.get("mimetype").asText());
        assertEquals(
                "v1=topic-value,v2=42,v3=true",
                node.get("data").asText());
    }
}
