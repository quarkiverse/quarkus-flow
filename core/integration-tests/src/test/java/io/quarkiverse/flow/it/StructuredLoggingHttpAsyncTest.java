package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkiverse.flow.Flow;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

/**
 * Tests that HTTP workflows with structured logging enabled complete successfully
 * without blocking the event loop thread.
 * <p>
 * This validates the fix for issue #458, where EventFormatter.formatWorkflowCompleted()
 * was calling instanceData().output() which blocks on CompletableFuture.join(),
 * causing the vert.x event loop to block and preventing REST responses from being sent.
 * <p>
 * The fix changed EventFormatter to use event.output() directly, which is already
 * available and doesn't require blocking.
 */
@QuarkusTest
@TestProfile(StructuredLoggingHttpAsyncTest.EnableStructuredLoggingWithPayloads.class)
@QuarkusTestResource(StructuredLoggingHttpAsyncTest.WireMockTestResource.class)
public class StructuredLoggingHttpAsyncTest {

    private static final String RESPONSE_JSON = """
            {
                "title": "Async Test Data",
                "status": "success",
                "value": 42
            }
            """;

    @Inject
    @Identifier("flow:http-async-completion")
    Flow httpAsyncCompletionFlow;

    @BeforeEach
    void setup() {
        // Reset WireMock to clear previous test's stubs and requests
        WireMockTestResource.server.resetAll();
    }

    @Test
    void shouldCompleteHttpWorkflowWithStructuredLoggingWithoutBlocking() throws Exception {
        // Given: WireMock server returns successful response
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE_JSON)));

        WorkflowInstance instance = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl()));

        // When: workflow is started
        CompletableFuture<WorkflowModel> future = instance.start();

        // Then: CompletableFuture completes successfully without blocking
        // (before the fix, this would timeout because EventFormatter blocked the event loop)
        WorkflowModel result = future.get(5, TimeUnit.SECONDS);

        // And: workflow output contains processed data
        Map<String, Object> output = result.asMap().orElseThrow();
        assertThat(output)
                .containsEntry("message", "Received: Async Test Data")
                .containsEntry("status", "success");
    }

    @Test
    void shouldLogWorkflowOutputInStructuredLogsWithoutBlocking() throws Exception {
        Assumptions.assumeTrue(
                Logger.getLogger("io.quarkiverse.flow.structuredlogging").isLoggable(Level.INFO),
                "Test skipped: No logging handlers found for structured logging category. Logging might be disabled globally.");

        // Given: WireMock server returns successful response
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE_JSON)));

        // When: workflow executes with structured logging enabled
        WorkflowInstance instance = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl()));
        instance.start().get(5, TimeUnit.SECONDS);

        // Then: structured log file should contain workflow completion event with output
        Path logFile = Path.of("target/quarkus-flow-events.log");
        assertThat(logFile).exists();

        List<String> logLines = Files.readAllLines(logFile);

        // Find the workflow.completed event
        boolean foundCompletionEventWithOutput = logLines.stream()
                .filter(line -> line.contains("workflow.completed"))
                .filter(line -> line.contains(instance.id()))
                .anyMatch(line -> line.contains("\"output\"") && line.contains("Received: Async Test Data"));

        assertThat(foundCompletionEventWithOutput)
                .as("Structured log should contain workflow.completed event with output for instance " + instance.id())
                .isTrue();
    }

    @Test
    void shouldHandleMultipleConcurrentHttpWorkflowsWithStructuredLogging() throws Exception {
        // Given: WireMock server returns successful responses
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE_JSON)));

        // When: multiple workflow instances are started concurrently
        CompletableFuture<WorkflowModel> future1 = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl())).start();
        CompletableFuture<WorkflowModel> future2 = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl())).start();
        CompletableFuture<WorkflowModel> future3 = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl())).start();

        // Then: all CompletableFutures complete successfully without blocking
        // (validates that structured logging doesn't cause blocking under concurrent load)
        CompletableFuture.allOf(future1, future2, future3).get(10, TimeUnit.SECONDS);

        assertThat(future1.get().asMap().orElseThrow())
                .containsEntry("message", "Received: Async Test Data");
        assertThat(future2.get().asMap().orElseThrow())
                .containsEntry("message", "Received: Async Test Data");
        assertThat(future3.get().asMap().orElseThrow())
                .containsEntry("message", "Received: Async Test Data");
    }

    public static class EnableStructuredLoggingWithPayloads implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.events", "*",
                    "quarkus.flow.structured-logging.include-workflow-payloads", "true",
                    "quarkus.flow.structured-logging.include-task-payloads", "true",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer server;
        static int port;

        static {
            try {
                port = HttpPortUtils.generateRandomPort();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(port);
            server.start();
            return Map.of();
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop();
            }
        }
    }
}
