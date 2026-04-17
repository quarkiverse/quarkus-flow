package io.quarkiverse.flow.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkiverse.flow.Flow;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

/**
 * Tests that HTTP workflows properly complete their CompletableFutures.
 * <p>
 * This validates the fix for issue #458, where HTTP tasks executed successfully
 * but the CompletableFuture never completed due to missing ManagedExecutor integration.
 */
@QuarkusTest
@QuarkusTestResource(HttpAsyncCompletionTest.WireMockTestResource.class)
public class HttpAsyncCompletionTest {

    private static final String RESPONSE_JSON = """
            {
                "title": "Test Data",
                "status": "success"
            }
            """;

    @Inject
    @Identifier("flow.HttpAsyncCompletion")
    Flow httpAsyncCompletionFlow;

    @BeforeEach
    void setup() {
        // Reset WireMock to clear previous test's stubs and requests
        WireMockTestResource.server.resetAll();
    }

    @Test
    void shouldCompleteHttpWorkflowSuccessfully() throws Exception {
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

        // Then: CompletableFuture completes successfully (this was failing before the fix)
        WorkflowModel result = future.get(5, TimeUnit.SECONDS);

        // And: workflow output contains processed data
        Map<String, Object> output = result.asMap().orElseThrow();
        assertThat(output).containsEntry("message", "Received: Test Data")
                .containsEntry("status", "success");
    }

    @Test
    void shouldCompleteMultipleHttpWorkflowsInParallel() throws Exception {
        // Given: WireMock server returns successful responses
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPONSE_JSON)));

        // When: multiple workflow instances are started in parallel
        CompletableFuture<WorkflowModel> future1 = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl())).start();
        CompletableFuture<WorkflowModel> future2 = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl())).start();
        CompletableFuture<WorkflowModel> future3 = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl())).start();

        // Then: all CompletableFutures complete successfully
        CompletableFuture.allOf(future1, future2, future3).get(10, TimeUnit.SECONDS);

        // And: all workflows produce correct output
        assertThat(future1.get().asMap().orElseThrow())
                .containsEntry("message", "Received: Test Data");
        assertThat(future2.get().asMap().orElseThrow())
                .containsEntry("message", "Received: Test Data");
        assertThat(future3.get().asMap().orElseThrow())
                .containsEntry("message", "Received: Test Data");
    }

    @Test
    void shouldHandleHttpErrorWithCompletedFuture() throws Exception {
        // Given: WireMock server returns error response
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/api/data"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        WorkflowInstance instance = httpAsyncCompletionFlow.instance(Map.of(
                "baseUrl", WireMockTestResource.server.baseUrl()));

        // When: workflow is started
        CompletableFuture<WorkflowModel> future = instance.start();

        // Then: CompletableFuture completes exceptionally (this validates error cases work)
        assertThat(future)
                .failsWithin(5, TimeUnit.SECONDS)
                .withThrowableThat()
                .withRootCauseInstanceOf(Exception.class);
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
