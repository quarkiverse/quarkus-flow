package io.quarkiverse.flow.deployment.test.devui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

public class FlowCachedDescriptorDevUIJsonRPCTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.http.port=0"), "application.properties")
                    .addClasses(DevUIWorkflow.class, CachedDevUIDescriptorObserver.class));

    public FlowCachedDescriptorDevUIJsonRPCTest() {
        super("quarkus-flow");
    }

    @Test
    public void shouldListOnlyDefinitionsFromDevUIBackend() throws Exception {
        JsonNode node = super.executeJsonRPCMethod("getWorkflows");
        assertEquals(2, node.size());
    }
}
