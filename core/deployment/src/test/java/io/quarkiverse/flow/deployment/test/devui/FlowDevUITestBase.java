package io.quarkiverse.flow.deployment.test.devui;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;

public class FlowDevUITestBase extends DevUIJsonRPCTest {

    public FlowDevUITestBase(String namespace, String testUrl) {
        super(namespace, testUrl);
    }

    @BeforeEach
    public void waitForAsyncWarmup() {
        Awaitility.await()
                .alias("Wait for WorkflowRegistry async warmup to complete")
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions() // If the websocket fails early, just keep trying
                .until(() -> {
                    JsonNode node = super.executeJsonRPCMethod("getNumbersOfWorkflows");
                    return node != null && node.asInt() > 0;
                });
    }
}
