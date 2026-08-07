package org.acme;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Disabled("Test for illustration/exemple only")
@QuarkusTest
class OpenTelemetryTest {

    private static final String RUNNER_URI = "q/flow/exec/otel/otel-showcase/1.0.0?wait=true";

    @Inject
    InMemorySpanExporter exporter;

    @ConfigProperty(name = "quarkus.flow.runner.security.api-keys.demo.secret", defaultValue = "please set in the application.properties")
    String demoSecret;

    @BeforeEach
    void setUp() {
        exporter.reset();
    }

    @Test
    void forCase() {
        executeWorkflow("{\"selectedCase\" : \"for\"}", demoSecret, 60, 120, 200);
        List<SpanData> spans = getWorkflowSpansAtMost("otel-showcase", "1.0.0", exporter, 8, 180);

        SpanData workflowSpan = getSpan(spans, "workflow.execute otel-showcase");
        assertThat(workflowSpan).isNotNull();
        SpanData selectCaseSpan = getSpan(spans, "task.execute Select Case");
        assertThat(selectCaseSpan).isNotNull();
        SpanData processCaseForSpan = getSpan(spans, "task.execute Process Case For");
        assertThat(processCaseForSpan).isNotNull();
        SpanData setIterationParametersSpan = getSpan(spans, "task.execute Set Iteration Parameters");
        assertThat(setIterationParametersSpan).isNotNull();
        SpanData forTaskSpan = getSpan(spans, "task.execute For Task");
        assertThat(forTaskSpan).isNotNull();
        SpanData emitEventIteration1 = getSpan(spans, "task.execute Emit Iteration Event", 1);
        assertThat(emitEventIteration1).isNotNull();
        SpanData emitEventIteration2 = getSpan(spans, "task.execute Emit Iteration Event", 2);
        assertThat(emitEventIteration2).isNotNull();
        SpanData emitEventIteration3 = getSpan(spans, "task.execute Emit Iteration Event", 3);
        assertThat(emitEventIteration3).isNotNull();

        assertThatIsChildOf(selectCaseSpan, workflowSpan);
        assertThatIsChildOf(processCaseForSpan, workflowSpan);
        assertThatIsChildOf(setIterationParametersSpan, processCaseForSpan);
        assertThatIsChildOf(forTaskSpan, processCaseForSpan);
        assertThatIsChildOf(emitEventIteration1, forTaskSpan);
        assertThatIsChildOf(emitEventIteration2, forTaskSpan);
        assertThatIsChildOf(emitEventIteration3, forTaskSpan);
    }

    @Test
    void forkCase() {
        executeWorkflow("{\"selectedCase\" : \"fork\"}", demoSecret, 60, 120, 200);
        List<SpanData> spans = getWorkflowSpansAtMost("otel-showcase", "1.0.0", exporter, 9, 180);

        SpanData workflowSpan = getSpan(spans, "workflow.execute otel-showcase");
        assertThat(workflowSpan).isNotNull();
        SpanData selectCaseSpan = getSpan(spans, "task.execute Select Case");
        assertThat(selectCaseSpan).isNotNull();
        SpanData processCaseForkSpan = getSpan(spans, "task.execute Process Case Fork");
        assertThat(processCaseForkSpan).isNotNull();
        SpanData setEventsParametersSpan = getSpan(spans, "task.execute Set Events Parameters");
        assertThat(setEventsParametersSpan).isNotNull();
        SpanData forkTaskSpan = getSpan(spans, "task.execute Fork Task");
        assertThat(forkTaskSpan).isNotNull();
        SpanData branch1Span = getSpan(spans, "task.execute Branch1");
        assertThat(branch1Span).isNotNull();
        SpanData emitBranch1Event = getSpan(spans, "task.execute Emit Branch1 Event");
        assertThat(emitBranch1Event).isNotNull();
        SpanData branch2Span = getSpan(spans, "task.execute Branch2");
        assertThat(branch2Span).isNotNull();
        SpanData emitBranch2Event = getSpan(spans, "task.execute Emit Branch2 Event");
        assertThat(emitBranch2Event).isNotNull();

        assertThatIsChildOf(selectCaseSpan, workflowSpan);
        assertThatIsChildOf(processCaseForkSpan, workflowSpan);
        assertThatIsChildOf(setEventsParametersSpan, processCaseForkSpan);
        assertThatIsChildOf(forkTaskSpan, processCaseForkSpan);
        assertThatIsChildOf(branch1Span, forkTaskSpan);
        assertThatIsChildOf(emitBranch1Event, branch1Span);
        assertThatIsChildOf(branch2Span, forkTaskSpan);
        assertThatIsChildOf(emitBranch2Event, branch2Span);
    }

    @Test
    void tryCase() {
        executeWorkflow("{\"selectedCase\" : \"try\"}", demoSecret, 60, 240, 500);
        List<SpanData> spans = getWorkflowSpansAtMost("otel-showcase", "1.0.0", exporter, 11, 180);

        SpanData workflowSpan = getSpan(spans, "workflow.execute otel-showcase");
        assertThat(workflowSpan).isNotNull();
        SpanData selectCaseSpan = getSpan(spans, "task.execute Select Case");
        assertThat(selectCaseSpan).isNotNull();
        SpanData processCaseTrySpan = getSpan(spans, "task.execute Process Case Try");
        assertThat(processCaseTrySpan).isNotNull();
        SpanData setAfterFailParametersSpan = getSpan(spans, "task.execute Set After Fail Parameters");
        assertThat(setAfterFailParametersSpan).isNotNull();
        SpanData tryTaskSpan = getSpan(spans, "task.execute Try Task");
        assertThat(tryTaskSpan).isNotNull();

        SpanData failingTaskIteration1Span = getSpan(spans, "task.execute Failing Task", 1);
        assertThat(failingTaskIteration1Span).isNotNull();
        SpanData afterFailingTaskIteration1Span = getSpan(spans, "task.execute Execute After Failing Task", 1);
        assertThat(afterFailingTaskIteration1Span).isNotNull();

        SpanData failingTaskIteration2Span = getSpan(spans, "task.execute Failing Task", 2);
        assertThat(failingTaskIteration2Span).isNotNull();
        SpanData afterFailingTaskIteration2Span = getSpan(spans, "task.execute Execute After Failing Task", 2);
        assertThat(afterFailingTaskIteration2Span).isNotNull();

        SpanData failingTaskIteration3Span = getSpan(spans, "task.execute Failing Task", 3);
        assertThat(failingTaskIteration3Span).isNotNull();
        SpanData afterFailingTaskIteration3Span = getSpan(spans, "task.execute Execute After Failing Task", 3);
        assertThat(afterFailingTaskIteration3Span).isNotNull();

        assertThatIsChildOf(selectCaseSpan, workflowSpan);
        assertThatIsChildOf(processCaseTrySpan, workflowSpan);
        assertThatIsChildOf(setAfterFailParametersSpan, processCaseTrySpan);
        assertThatIsChildOf(tryTaskSpan, processCaseTrySpan);
        assertThatIsChildOf(failingTaskIteration1Span, tryTaskSpan);
        assertThatIsChildOf(afterFailingTaskIteration1Span, tryTaskSpan);
        assertThatIsChildOf(failingTaskIteration2Span, tryTaskSpan);
        assertThatIsChildOf(afterFailingTaskIteration2Span, tryTaskSpan);
        assertThatIsChildOf(failingTaskIteration3Span, tryTaskSpan);
        assertThatIsChildOf(afterFailingTaskIteration3Span, tryTaskSpan);
    }

    @Test
    void unknownCase() {
        executeWorkflow("{}", demoSecret, 60, 120, 200);
        List<SpanData> spans = getWorkflowSpansAtMost("otel-showcase", "1.0.0", exporter, 5, 180);

        SpanData workflowSpan = getSpan(spans, "workflow.execute otel-showcase");
        assertThat(workflowSpan).isNotNull();
        SpanData selectCaseSpan = getSpan(spans, "task.execute Select Case");
        assertThat(selectCaseSpan).isNotNull();
        SpanData handleUnknownCaseSpan = getSpan(spans, "task.execute Handle Unknown Case");
        assertThat(handleUnknownCaseSpan).isNotNull();
        SpanData setUnknownCaseEventParamsSpan = getSpan(spans, "task.execute Set Unknown Case Event Parameters");
        assertThat(setUnknownCaseEventParamsSpan).isNotNull();
        SpanData emitUnknownCaseEvent = getSpan(spans, "task.execute Emit Unknown Case Event");
        assertThat(emitUnknownCaseEvent).isNotNull();

        assertThatIsChildOf(selectCaseSpan, workflowSpan);
        assertThatIsChildOf(handleUnknownCaseSpan, workflowSpan);
        assertThatIsChildOf(setUnknownCaseEventParamsSpan, handleUnknownCaseSpan);
        assertThatIsChildOf(emitUnknownCaseEvent, handleUnknownCaseSpan);
    }

    private static void assertThatIsChildOf(SpanData child, SpanData expectedParent) {
        assertThat(child.getTraceId())
                .withFailMessage("Spans don't belong to the same trace child: '%s', expectedParent: '%s'", child.getName(),
                        expectedParent.getName())
                .isEqualTo(expectedParent.getTraceId());
        assertThat(child.getParentSpanId())
                .withFailMessage("Span child: '%s' is not children of expectedParent: '%s'", child.getName(),
                        expectedParent.getName())
                .isEqualTo(expectedParent.getSpanId());
    }

    private static JsonPath executeWorkflow(String input, String secret, int connectTimeoutInSeconds,
            int executionTimeoutInSeconds, int expectedStatusCode) {
        return RestAssured.given()
                .config(RestAssuredConfig.config().httpClient(
                        HttpClientConfig.httpClientConfig().reuseHttpClientInstance()
                                .setParam("http.connection.timeout",
                                        (int) Duration.ofSeconds(connectTimeoutInSeconds).toMillis())
                                .setParam("http.socket.timeout",
                                        (int) Duration.ofSeconds(executionTimeoutInSeconds).toMillis())))
                .header("Authorization", "Bearer " + secret)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(input)
                .post(RUNNER_URI)
                .then()
                .statusCode(expectedStatusCode)
                .extract()
                .jsonPath();
    }

    private static List<SpanData> getWorkflowSpansAtMost(String workflowName, String workflowVersion,
            InMemorySpanExporter exporter,
            int size, int timeoutInSeconds) {
        AtomicReference<List<SpanData>> spans = new AtomicReference<>();
        AtomicReference<InMemorySpanExporter> spanExporter = new AtomicReference<>(exporter);
        await().atMost(timeoutInSeconds, TimeUnit.SECONDS)
                .until(() -> {
                    spans.set(filterWorkflowSpans(spanExporter.get().getFinishedSpanItems(), workflowName, workflowVersion));
                    return spans.get().size() == size;
                });
        return spans.get();
    }

    private static Predicate<Map<AttributeKey<?>, Object>> byNameAndVersionFilter(String workflow, String version) {
        return attributes -> workflow.equals(attributes.get(AttributeKey.stringKey("flow.workflow.name")))
                && version.equals(attributes.get(AttributeKey.stringKey("flow.workflow.version")));
    }

    private static List<SpanData> filterWorkflowSpans(List<SpanData> spans, String workflow, String version) {
        return spans.stream().filter(span -> {
            Map<AttributeKey<?>, Object> attributes = span.getAttributes().asMap();
            return byNameAndVersionFilter(workflow, version).test(attributes);
        }).collect(Collectors.toList());
    }

    private static SpanData getSpan(List<SpanData> spans, String spanName) {
        return spans.stream().filter(span -> Objects.equals(span.getName(), spanName)).findFirst().orElse(null);
    }

    private static SpanData getSpan(List<SpanData> spans, String spanName, long iteration) {
        return spans.stream()
                .filter(span -> Objects.equals(span.getName(), spanName))
                .filter(span -> Objects.equals(span.getAttributes().get(AttributeKey.longKey("flow.task.iteration")),
                        iteration))
                .findFirst().orElse(null);
    }
}
