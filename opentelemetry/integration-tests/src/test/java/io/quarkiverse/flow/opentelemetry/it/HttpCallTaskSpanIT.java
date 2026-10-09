package io.quarkiverse.flow.opentelemetry.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/**
 * HTTP call tasks finish their work asynchronously after {@code CallableTask.apply} returns, so
 * {@code OTelTaskSpanProxy} does not make their task span current. Their client spans keep the parent they get from
 * the caller's context, here the incoming request's server span, and must never be attached to a task span: when HTTP
 * tasks were not excluded, some runs attached a call to the span of a different task.
 * <p>
 * Each workflow is run several times because the wrong parent only showed up in some runs.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/pull/1065#issuecomment-6082482137">PR #1065 discussion</a>
 */
@QuarkusTest
class HttpCallTaskSpanIT {

    private static final int RUNS = 10;
    private static final String SERVER_SPAN_PREFIX = "POST /otel-workflows/";

    @Inject
    InMemorySpanExporter exporter;

    @TestHTTPResource
    URL baseUrl;

    @BeforeEach
    void reset() {
        exporter.reset();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({ "otel-http-simple, 2", "otel-http-fork, 3" })
    @DisplayName("test_http_call_spans_are_never_attached_to_a_task_span")
    void test_http_call_spans_are_never_attached_to_a_task_span(String workflow, int httpCalls) {
        for (int run = 0; run < RUNS; run++) {
            exporter.reset();
            RestAssured.given()
                    .contentType("application/json")
                    .body(Map.of("baseUrl", baseUrl.toString().replaceAll("/$", "")))
                    .post("/otel-workflows/" + workflow)
                    .then()
                    .statusCode(200);

            List<SpanData> spans = awaitSpans(workflow, httpCalls);
            Map<String, SpanData> byId = spans.stream()
                    .collect(Collectors.toMap(SpanData::getSpanId, Function.identity(), (a, b) -> a));
            SpanData serverSpan = spans.stream()
                    .filter(span -> span.getName().equals(SERVER_SPAN_PREFIX + workflow))
                    .findFirst()
                    .orElseThrow();
            int currentRun = run;

            assertThat(spans)
                    .filteredOn(span -> span.getKind() == SpanKind.CLIENT)
                    .as("HTTP client spans of run %d", currentRun)
                    .hasSize(httpCalls)
                    .allSatisfy(client -> assertThat(client.getParentSpanId())
                            .as("parent of %s in run %d (%s)", client.getName(), currentRun,
                                    byId.containsKey(client.getParentSpanId())
                                            ? byId.get(client.getParentSpanId()).getName()
                                            : "not exported")
                            .isEqualTo(serverSpan.getSpanId()));
        }
    }

    private List<SpanData> awaitSpans(String workflow, int httpCalls) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> {
                    List<SpanData> spans = exporter.getFinishedSpanItems();
                    return spans.stream().filter(span -> span.getKind() == SpanKind.CLIENT).count() >= httpCalls
                            && spans.stream().anyMatch(span -> span.getName().equals(SERVER_SPAN_PREFIX + workflow));
                });
        return exporter.getFinishedSpanItems();
    }
}
