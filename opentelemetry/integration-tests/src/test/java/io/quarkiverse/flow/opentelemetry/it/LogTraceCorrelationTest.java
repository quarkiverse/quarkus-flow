package io.quarkiverse.flow.opentelemetry.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.flow.tracing.TraceCorrelationProvider;
import io.quarkiverse.flow.tracing.TraceCorrelationProvider.TraceContext;
import io.quarkiverse.flow.tracing.TraceCorrelationProviders;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.serverlessworkflow.impl.ServicePriority;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

@QuarkusTest
@DisplayName("flow lifecycle events correlate with the active OpenTelemetry trace")
class LogTraceCorrelationTest {

    private static final AttributeKey<String> WF_NAME = AttributeKey.stringKey("flow.workflow.name");
    private static final AttributeKey<String> TASK_NAME = AttributeKey.stringKey("flow.task.name");

    @Inject
    InMemorySpanExporter exporter;

    @Inject
    CapturingListener capturingListener;

    @BeforeEach
    void setUp() {
        exporter.reset();
        capturingListener.captured.clear();
    }

    @Test
    @DisplayName("terminal_events_resolve_the_matching_trace_and_span_ids")
    void terminal_events_resolve_the_matching_trace_and_span_ids() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body("{}")
                .post("/otel-workflows/otel-set-task")
                .then()
                .statusCode(200);

        await().atMost(30, TimeUnit.SECONDS).until(() -> exporter.getFinishedSpanItems().size() >= 3);
        List<SpanData> spans = exporter.getFinishedSpanItems();

        SpanData workflowSpan = spans.stream()
                .filter(s -> "otel-set-task".equals(s.getAttributes().get(WF_NAME)))
                .filter(s -> s.getAttributes().get(TASK_NAME) == null)
                .filter(s -> s.getName().startsWith("workflow.execute"))
                .findFirst().orElseThrow(() -> new AssertionError("workflow span not exported: " + spans));
        SpanData taskSpan = spans.stream()
                .filter(s -> "setTask".equals(s.getAttributes().get(TASK_NAME)))
                .findFirst().orElseThrow(() -> new AssertionError("task span not exported: " + spans));

        await().atMost(10, TimeUnit.SECONDS).until(() -> capturingListener.captured.containsKey("task.completed:setTask")
                && capturingListener.captured.containsKey("workflow.completed"));

        TraceContext taskCompleted = capturingListener.captured.get("task.completed:setTask");
        assertThat(taskCompleted.traceId()).isEqualTo(workflowSpan.getTraceId());
        assertThat(taskCompleted.spanId()).isEqualTo(taskSpan.getSpanId());
        assertThat(taskCompleted.sampled()).isEqualTo("true");

        TraceContext workflowCompleted = capturingListener.captured.get("workflow.completed");
        assertThat(workflowCompleted.traceId()).isEqualTo(workflowSpan.getTraceId());
        assertThat(workflowCompleted.spanId()).isEqualTo(workflowSpan.getSpanId());
    }

    /**
     * A {@link WorkflowExecutionListener} that runs after every other listener (highest priority
     * value) and records what the {@link TraceCorrelationProvider} resolves for each terminal
     * event - i.e. exactly what the flow loggers would put on their log line.
     * <p>
     * Resolves the provider via {@link TraceCorrelationProviders#resolve}, exactly once at
     * construction, like every other consumer must: that's what registers it as an expected
     * reader of a task's terminal trace context, so the OTel implementation doesn't release the
     * entry before this listener (which runs last) gets to read it too.
     */
    @Singleton
    static class CapturingListener implements WorkflowExecutionListener {

        final Map<String, TraceContext> captured = new ConcurrentHashMap<>();

        @Inject
        Instance<TraceCorrelationProvider> providers;

        private TraceCorrelationProvider traceCorrelation;

        @PostConstruct
        void init() {
            traceCorrelation = TraceCorrelationProviders.resolve(providers);
        }

        @Override
        public void onTaskCompleted(TaskCompletedEvent ev) {
            traceCorrelation.traceContextFor(ev)
                    .ifPresent(tc -> captured.put("task.completed:" + ev.taskContext().taskName(), tc));
        }

        @Override
        public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
            traceCorrelation.traceContextFor(ev).ifPresent(tc -> captured.put("workflow.completed", tc));
        }

        @Override
        public int priority() {
            return ServicePriority.DEFAULT_PRIORITY + 1000;
        }
    }
}
