package io.quarkiverse.flow.opentelemetry.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * A workflow task that invokes a LangChain4j agentic system ({@code sequence(classify, parallel(details, summary))})
 * must produce a single trace, with every span under its correct parent.
 * <p>
 * The Ollama client's own HTTP {@code POST} spans are left out: they start a new trace even when a span is current and
 * Quarkus Flow is not involved at all (a plain {@code ChatModel.chat(..)} call inside an active span shows the same), so
 * they are outside what Quarkus Flow can propagate.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1056">#1056</a>
 */
@QuarkusTest
@QuarkusTestResource(value = OllamaMockResource.class, restrictToAnnotatedClass = true)
class AgenticTraceContextIT {

    private static final String WORKFLOW_SPAN_PREFIX = "workflow.execute ";
    private static final String TASK_SPAN_PREFIX = "task.execute ";
    private static final String AI_SERVICE_SPAN_PREFIX = "langchain4j.aiservices.";

    @Inject
    AgenticTracingFlow flow;

    @Inject
    InMemorySpanExporter exporter;

    @BeforeEach
    void reset() {
        exporter.reset();
        AgenticTracingFlow.lastCurrentSpanId = null;
    }

    @Test
    @DisplayName("test_task_span_is_current_inside_the_task_body")
    void test_task_span_is_current_inside_the_task_body() throws Exception {
        List<SpanData> spans = run();

        SpanData taskSpan = span(spans, TASK_SPAN_PREFIX + AgenticTracingFlow.TASK_NAME);
        assertThat(AgenticTracingFlow.lastCurrentSpanId)
                .as("Span.current() inside the task body")
                .isEqualTo(taskSpan.getSpanId());
    }

    @Test
    @DisplayName("test_agentic_workflow_produces_a_single_trace")
    void test_agentic_workflow_produces_a_single_trace() throws Exception {
        List<SpanData> spans = run();
        printTraces(spans);

        // 3 workflows, 6 tasks, and for each of the 3 agents an AI service span and a model completion span
        String trace = span(spans, WORKFLOW_SPAN_PREFIX + AgenticTracingFlow.NAME).getTraceId();
        assertThat(spans)
                .filteredOn(span -> span.getKind() != SpanKind.CLIENT)
                .hasSize(15)
                .allSatisfy(span -> assertThat(span.getTraceId()).as(span.getName()).isEqualTo(trace));
    }

    @Test
    @DisplayName("test_generated_agentic_workflows_are_children_of_the_invoking_task")
    void test_generated_agentic_workflows_are_children_of_the_invoking_task() throws Exception {
        List<SpanData> spans = run();
        Map<String, SpanData> byId = spans.stream().collect(Collectors.toMap(SpanData::getSpanId, Function.identity()));

        List<SpanData> generatedWorkflows = spans.stream()
                .filter(span -> span.getName().startsWith(WORKFLOW_SPAN_PREFIX))
                .filter(span -> !span.getName().equals(WORKFLOW_SPAN_PREFIX + AgenticTracingFlow.NAME))
                .toList();

        assertThat(generatedWorkflows).hasSize(2)
                .allSatisfy(span -> assertThat(byId.get(span.getParentSpanId()))
                        .as("parent of %s", span.getName())
                        .isNotNull()
                        .extracting(SpanData::getName)
                        .asString()
                        .startsWith(TASK_SPAN_PREFIX));
    }

    @Test
    @DisplayName("test_model_calls_are_children_of_the_task_that_ran_their_agent")
    void test_model_calls_are_children_of_the_task_that_ran_their_agent() throws Exception {
        List<SpanData> spans = run();
        Map<String, SpanData> byId = spans.stream().collect(Collectors.toMap(SpanData::getSpanId, Function.identity()));

        List<SpanData> aiServiceCalls = spans.stream()
                .filter(span -> span.getName().startsWith(AI_SERVICE_SPAN_PREFIX))
                .toList();

        assertThat(aiServiceCalls)
                .as("one model call per agent: classify, details, summary")
                .hasSize(3)
                .allSatisfy(span -> assertThat(byId.get(span.getParentSpanId()))
                        .as("parent of %s", span.getName())
                        .isNotNull()
                        .extracting(SpanData::getName)
                        .asString()
                        .startsWith(TASK_SPAN_PREFIX)
                        .isNotEqualTo(TASK_SPAN_PREFIX + AgenticTracingFlow.TASK_NAME));
    }

    private List<SpanData> run() throws Exception {
        flow.instance(new AgenticTracingFlow.Email("My car was stolen.")).start().get(30, TimeUnit.SECONDS);

        // main workflow + the 2 generated agentic workflows, all ended
        await().atMost(Duration.ofSeconds(10))
                .until(() -> exporter.getFinishedSpanItems().stream()
                        .filter(span -> span.getName().startsWith(WORKFLOW_SPAN_PREFIX))
                        .count() >= 3);
        return exporter.getFinishedSpanItems();
    }

    private static SpanData span(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No span named " + name + " in " + spans.stream()
                        .map(SpanData::getName).toList()));
    }

    private static void printTraces(List<SpanData> spans) {
        Map<String, String> names = spans.stream()
                .collect(Collectors.toMap(SpanData::getSpanId, SpanData::getName, (a, b) -> a));
        Map<String, List<SpanData>> byTrace = spans.stream().collect(Collectors.groupingBy(SpanData::getTraceId));
        System.out.printf("%n%d spans in %d trace(s)%n", spans.size(), byTrace.size());
        byTrace.forEach((trace, traceSpans) -> {
            System.out.println("trace " + trace);
            traceSpans.forEach(span -> System.out.printf("    %-60s parent=%s%n", span.getName(),
                    span.getParentSpanContext().isValid()
                            ? names.getOrDefault(span.getParentSpanId(), span.getParentSpanId())
                            : "<root>"));
        });
    }
}
