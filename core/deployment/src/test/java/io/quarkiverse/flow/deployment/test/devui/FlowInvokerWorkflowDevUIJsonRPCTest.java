package io.quarkiverse.flow.deployment.test.devui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

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
                "id", WorkflowDefinitionId.of(new AgenticDevUIWorkflow().descriptor()),
                "input", """
                        {
                          "var1": "topic-value",
                          "var2": 42,
                          "var3": true
                        }
                        """));

        assertEquals(MediaType.APPLICATION_JSON, node.get("mimetype").asText());
        JsonNode data = node.get("data");
        assertNotNull(data);

        // Payload returned by DevUIAgenticServiceBean.complex(...)
        assertEquals("topic-value", data.get("var1").asText());
        assertEquals(42, data.get("var2").asInt());
        assertEquals(true, data.get("var3").asBoolean());
    }
}
