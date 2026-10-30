package io.quarkiverse.flow.opentelemetry.it;

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
    private static final String WORKFLOW_CREATE_SPAN_PREFIX = "workflow.create ";
    private static final String TASK_SPAN_PREFIX = "task.execute ";
    private static final String AI_SERVICE_SPAN_PREFIX = "langchain4j.aiservices.";

    @Inject
    AgenticTracingFlow flow;

    @Inject
    OptInAgentFlow optInAgentFlow;

    @Inject
    AgentTaskFlow agentTaskFlow;

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

        // 3 workflows (a create and an execute span each), 6 tasks, and for each of the 3 agents an AI service span
        // and a model completion span
        String trace = span(spans, WORKFLOW_SPAN_PREFIX + AgenticTracingFlow.NAME).getTraceId();
        assertThat(spans)
                .filteredOn(span -> span.getKind() != SpanKind.CLIENT)
                .hasSize(18)
                .allSatisfy(span -> assertThat(span.getTraceId()).as(span.getName()).isEqualTo(trace));
    }

    @Test
    @DisplayName("test_generated_agentic_workflows_are_children_of_the_invoking_task")
    void test_generated_agentic_workflows_are_children_of_the_invoking_task() throws Exception {
        List<SpanData> spans = run();
        Map<String, SpanData> byId = spans.stream().collect(Collectors.toMap(SpanData::getSpanId, Function.identity()));

        // each workflow.execute span is a child of its workflow.create span, which is created by the invoking task
        List<SpanData> generatedWorkflows = spans.stream()
                .filter(span -> span.getName().startsWith(WORKFLOW_CREATE_SPAN_PREFIX))
                .filter(span -> !span.getName().equals(WORKFLOW_CREATE_SPAN_PREFIX + AgenticTracingFlow.NAME))
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

    @Test
    @DisplayName("test_generated_agentic_workflow_spans_are_typed_invoke_agent")
    void test_generated_agentic_workflow_spans_are_typed_invoke_agent() throws Exception {
        List<SpanData> spans = run();

        // IntakeAgents.process (@SequenceAgent) and ExtractionAgent.extract (@ParallelAgent)
        assertThat(spans)
                .filteredOn(span -> span.getName().startsWith(WORKFLOW_SPAN_PREFIX))
                .filteredOn(span -> !span.getName().equals(WORKFLOW_SPAN_PREFIX + AgenticTracingFlow.NAME))
                .extracting(span -> span.getAttributes().get(GEN_AI_OPERATION_NAME),
                        span -> span.getAttributes().get(GEN_AI_AGENT_NAME))
                .containsExactlyInAnyOrder(
                        tuple(INVOKE_AGENT, "process"),
                        tuple(INVOKE_AGENT, "extract"));
    }

    @Test
    @DisplayName("test_plain_workflow_and_task_spans_are_not_genai_typed")
    void test_plain_workflow_and_task_spans_are_not_genai_typed() throws Exception {
        List<SpanData> spans = run();

        assertThat(span(spans, WORKFLOW_SPAN_PREFIX + AgenticTracingFlow.NAME).getAttributes().get(GEN_AI_OPERATION_NAME))
                .as("the plain workflow invoking the agents")
                .isNull();
        assertThat(spans)
                .filteredOn(span -> span.getName().startsWith(TASK_SPAN_PREFIX)
                        || span.getName().startsWith(WORKFLOW_CREATE_SPAN_PREFIX))
                .isNotEmpty()
                .allSatisfy(span -> assertThat(span.getAttributes().get(GEN_AI_OPERATION_NAME))
                        .as(span.getName())
                        .isNull());
    }

    @Test
    @DisplayName("test_workflow_opting_in_through_document_metadata_is_typed_invoke_agent")
    void test_workflow_opting_in_through_document_metadata_is_typed_invoke_agent() throws Exception {
        optInAgentFlow.instance("hello").start().get(30, TimeUnit.SECONDS);

        String spanName = WORKFLOW_SPAN_PREFIX + OptInAgentFlow.NAME;
        await().atMost(Duration.ofSeconds(10))
                .until(() -> exporter.getFinishedSpanItems().stream()
                        .anyMatch(span -> spanName.equals(span.getName())));

        assertThat(span(exporter.getFinishedSpanItems(), spanName).getAttributes().asMap())
                .containsEntry(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .containsEntry(GEN_AI_AGENT_NAME, OptInAgentFlow.AGENT_NAME)
                .containsEntry(GEN_AI_AGENT_DESCRIPTION, OptInAgentFlow.SUMMARY);
    }

    @Test
    @DisplayName("test_dsl_agent_task_span_is_typed_invoke_agent")
    void test_dsl_agent_task_span_is_typed_invoke_agent() throws Exception {
        agentTaskFlow.instance("Hello").start().get(30, TimeUnit.SECONDS);

        String workflowSpanName = WORKFLOW_SPAN_PREFIX + AgentTaskFlow.NAME;
        await().atMost(Duration.ofSeconds(10))
                .until(() -> exporter.getFinishedSpanItems().stream()
                        .anyMatch(span -> workflowSpanName.equals(span.getName())));
        List<SpanData> spans = exporter.getFinishedSpanItems();

        assertThat(span(spans, TASK_SPAN_PREFIX + AgentTaskFlow.AGENT_TASK_NAME).getAttributes().asMap())
                .containsEntry(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .containsEntry(GEN_AI_AGENT_NAME, AgentTaskFlow.AGENT_TASK_NAME);
        assertThat(List.of(span(spans, TASK_SPAN_PREFIX + AgentTaskFlow.PLAIN_TASK_NAME), span(spans, workflowSpanName)))
                .as("the plain task and the workflow are not agent invocations")
                .allSatisfy(span -> assertThat(span.getAttributes().get(GEN_AI_OPERATION_NAME))
                        .as(span.getName())
                        .isNull());
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
