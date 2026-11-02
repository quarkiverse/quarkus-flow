package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static io.quarkiverse.flow.dsl.FlowDSL.agent;
import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.withUniqueId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkiverse.flow.dsl.types.UniqueIdBiFunction;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.Workflow;

/**
 * Verifies that {@link SpanUtils#enrichAgentCall(SpanBuilder, TaskBase, String)} types the spans of tasks created
 * with the Java DSL {@code agent(..)}, and leaves every other task untouched.
 */
class SpanUtilsTest {

    private static final UniqueIdBiFunction<String, String> AGENT_FN = (uniqueId, payload) -> payload;

    private static final Workflow WORKFLOW = FlowWorkflowBuilder.workflow("agent-task")
            .tasks(agent("triageAgent", AGENT_FN, String.class),
                    withUniqueId("uniqueIdTask", AGENT_FN, String.class),
                    function("plainTask", (String input) -> input, String.class))
            .build();

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    @DisplayName("test_agent_task_span_is_typed_invoke_agent")
    void test_agent_task_span_is_typed_invoke_agent() {
        assertThat(enrichedSpan(task("triageAgent"), "triageAgent"))
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "triageAgent")
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .as("a task has no summary, so there is no agent description")
                        .doesNotContainKey(GEN_AI_AGENT_DESCRIPTION));
    }

    @Test
    @DisplayName("test_unique_id_task_span_is_not_typed")
    void test_unique_id_task_span_is_not_typed() {
        assertThat(enrichedSpan(task("uniqueIdTask"), "uniqueIdTask"))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .as("withUniqueId(..) is not an agent invocation")
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
    }

    @Test
    @DisplayName("test_plain_function_task_span_is_not_typed")
    void test_plain_function_task_span_is_not_typed() {
        assertThat(enrichedSpan(task("plainTask"), "plainTask"))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
    }

    @Test
    @DisplayName("test_non_java_tasks_are_not_typed")
    void test_non_java_tasks_are_not_typed() {
        assertThat(enrichedSpan(new CallHTTP(), "httpTask"))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME));
        assertThat(enrichedSpan(new CallFunction().withCall("Java"), "noArgumentsTask"))
                .as("a Java call without arguments is not an agent invocation")
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME));
        assertThat(enrichedSpan(null, "noTask"))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME));
    }

    private SpanData enrichedSpan(TaskBase task, String taskName) {
        exporter.reset();
        SpanBuilder builder = tracerProvider.get("test").spanBuilder("task.execute " + taskName);
        SpanUtils.enrichAgentCall(builder, task, taskName);
        builder.startSpan().end();
        return exporter.getFinishedSpanItems()
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private static TaskBase task(String name) {
        return WORKFLOW.getDo()
                .stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .map(item -> (TaskBase) item.getTask().getCallTask().get())
                .orElseThrow();
    }
}
