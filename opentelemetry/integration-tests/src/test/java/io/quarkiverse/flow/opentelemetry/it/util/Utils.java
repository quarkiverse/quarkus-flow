package io.quarkiverse.flow.opentelemetry.it.util;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;

public class Utils {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static JsonPath executeWorkflow(String workflow, String input, int connectTimeoutInSeconds,
            int executionTimeoutInSeconds) {
        return executeWorkflow(workflow, input, connectTimeoutInSeconds, executionTimeoutInSeconds, 500);
    }

    public static JsonPath executeWorkflow(String workflow, String input, int connectTimeoutInSeconds,
            int executionTimeoutInSeconds, int expectedStatusCode) {
        return RestAssured.given()
                .config(RestAssuredConfig.config().httpClient(
                        HttpClientConfig.httpClientConfig().reuseHttpClientInstance()
                                .setParam("http.connection.timeout",
                                        (int) Duration.ofSeconds(connectTimeoutInSeconds).toMillis())
                                .setParam("http.socket.timeout",
                                        (int) Duration.ofSeconds(executionTimeoutInSeconds).toMillis())))
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(input)
                .post(workflow)
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .jsonPath();
    }

    public static List<SpanInfo> getWorkflowSpans(String workflow, String version) {
        return getSpans().stream().filter(item -> {
            Map<String, Object> attributes = (Map<String, Object>) item.get("attributes");
            return workflow.equals(attributes.get("flow.workflow.name"))
                    && version.equals(attributes.get("flow.workflow.version"));
        }).map(item -> OBJECT_MAPPER.convertValue(item, SpanInfo.class)).collect(Collectors.toList());
    }

    public static List<Map<String, Object>> getSpans() {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .get("/otel-span-exporter/spans")
                .body().as(new TypeRef<>() {
                });
    }

    public static void deleteSpans() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .delete("/otel-span-exporter/reset-spans")
                .then()
                .statusCode(204);
    }
}
