package io.quarkiverse.flow.dsl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.quarkiverse.flow.dsl.FlowDSL.http;
import static io.quarkiverse.flow.dsl.TestSerializationUtils.writeAndReadInMemory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowInstance;

@QuarkusTest
@QuarkusTestResource(FuncHttpTest.WireMockTestResource.class)
public class FuncHttpTest {

    @Inject
    WorkflowApplication app;

    @BeforeEach
    void setup() {
        WireMockTestResource.server.resetAll();
        WireMockTestResource.server.stubFor(get(urlPathEqualTo("/api/endpoint"))
                .willReturn(aResponse()
                        .withStatus(204)
                        .withHeader("Content-Type", "application/json")));
    }

    private LoggedRequest takeRequestOrFail() {
        List<LoggedRequest> requests = WireMockTestResource.server
                .findAll(getRequestedFor(urlPathEqualTo("/api/endpoint")));
        assertThat(requests)
                .as("Expected an HTTP request to be received by WireMock")
                .hasSize(1);
        return requests.get(0);
    }

    @Test
    @DisplayName("Query method with single key-value pair")
    void test_query_with_single_key_value() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-single")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .query("param1", "value1"))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();

        assertSoftly(
                softly -> {
                    softly.assertThat(request.getAbsoluteUrl()).contains("param1=value1");
                    softly.assertThat(request.getMethod().getName()).isEqualTo("GET");
                });
    }

    @Test
    @DisplayName("Query method with multiple single key-value pairs (individually tested)")
    void test_query_with_multiple_single_values() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-single-multi")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .query("param1", "value1")
                                        .query("param2", "value2")
                                        .query("param3", "value3"))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();
        String url = request.getAbsoluteUrl();

        assertSoftly(
                softly -> {
                    softly.assertThat(url).contains("param1=value1").isNotEmpty();
                    softly.assertThat(url).contains("param2=value2").isNotEmpty();
                    softly.assertThat(url).contains("param3=value3").isNotEmpty();
                });
    }

    @Test
    @DisplayName("Query method with Map of parameters")
    void test_query_with_map() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-map")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .query(Map.of("userId", "123", "userName", "john", "status", "active")))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();
        String url = request.getAbsoluteUrl();

        assertSoftly(
                softly -> {
                    softly.assertThat(url).contains("userId=123");
                    softly.assertThat(url).contains("userName=john");
                    softly.assertThat(url).contains("status=active");
                });
    }

    @Test
    @DisplayName("Query method with expression string")
    void test_query_with_expression() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-expression")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .query("enabled", "${ .enabled }"))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of("enabled", true));
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();

        assertThat(request.getAbsoluteUrl()).contains("enabled=true");
    }

    @Test
    @DisplayName("Query method with empty Map")
    void test_query_with_empty_map() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-empty-map")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .query(Map.of()))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();

        assertSoftly(
                softly -> {
                    softly.assertThat(request.getUrl()).isEqualTo("/api/endpoint");
                    softly.assertThat(request.queryParameter("nonExistent").isPresent()).isFalse();
                });
    }

    @Test
    @DisplayName("Query method with special characters in values")
    void test_query_with_special_characters() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-special-chars")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .query("email", "user@example.com"))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();

        assertSoftly(
                softly -> {
                    softly.assertThat(request.queryParameter("email").firstValue()).isEqualTo("user@example.com");
                    softly.assertThat(request.getAbsoluteUrl()).contains("email=user%40example.com");
                });
    }

    @Test
    @DisplayName("Query method overload - Map with multiple values")
    void test_query_map_multiple_values() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-map-multi")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .query(Map.of("limit", "50", "offset", "0", "sort", "name")))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();
        String url = request.getAbsoluteUrl();

        assertSoftly(
                softly -> {
                    softly.assertThat(url).contains("limit=50");
                    softly.assertThat(url).contains("offset=0");
                    softly.assertThat(url).contains("sort=name");
                });
    }

    @Test
    @DisplayName("Query method with headers and query parameters")
    void test_query_with_headers_and_query() throws Exception {
        var workflow = writeAndReadInMemory(
                FlowWorkflowBuilder.workflow("test-query-with-headers")
                        .tasks(
                                http("callHttp")
                                        .get()
                                        .uri(WireMockTestResource.server.baseUrl() + "/api/endpoint")
                                        .header("Authorization", "Bearer token123")
                                        .header("Accept", "application/json")
                                        .query("userId", "123"))
                        .build());

        WorkflowInstance instance = app.workflowDefinition(workflow).instance(Map.of());
        instance.start().join();

        LoggedRequest request = takeRequestOrFail();
        String url = request.getAbsoluteUrl();
        assertThat(url).contains("userId=123");
    }

    public static class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

        static WireMockServer server;

        @Override
        public Map<String, String> start() {
            server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
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
