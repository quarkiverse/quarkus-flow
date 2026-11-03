package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.GEN_AI_INVOKE_AGENT_EVENT;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * Verifies that {@link OTelAgentInvocationListener} types the Flow task span referenced by {@link FlowTaskSpanRef}
 * as a GenAI {@code invoke_agent} operation, first-wins, and always records a {@code gen_ai.invoke_agent} event.
 */
class OTelAgentInvocationListenerTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    private final Tracer tracer = tracerProvider.get("test");

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    @DisplayName("test_does_nothing_without_an_enclosing_task_span")
    void test_does_nothing_without_an_enclosing_task_span() {
        Assertions.assertThatCode(() -> new OTelAgentInvocationListener(true).onAgentInvoked("triageAgent"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_single_agent_types_the_span_and_adds_one_event")
    void test_single_agent_types_the_span_and_adds_one_event() {
        SpanData span = invoke(true, "triageAgent");

        assertThat(span)
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "triageAgent");
        assertThat(span.getEvents())
                .extracting(EventData::getName)
                .containsExactly(GEN_AI_INVOKE_AGENT_EVENT);
    }

    @Test
    @DisplayName("test_two_distinct_agents_keep_the_first_agent_attributes_and_add_two_events")
    void test_two_distinct_agents_keep_the_first_agent_attributes_and_add_two_events() {
        SpanData span = invoke(true, "triageAgent", "summaryAgent");

        assertThat(span)
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "triageAgent");
        assertThat(span.getEvents())
                .extracting(EventData::getName)
                .containsExactly(GEN_AI_INVOKE_AGENT_EVENT, GEN_AI_INVOKE_AGENT_EVENT);
        Assertions.assertThat(span.getEvents())
                .extracting(event -> event.getAttributes().get(GEN_AI_AGENT_NAME))
                .containsExactly("triageAgent", "summaryAgent");
    }

    @Test
    @DisplayName("test_same_agent_invoked_twice_sets_attributes_once_and_adds_two_events")
    void test_same_agent_invoked_twice_sets_attributes_once_and_adds_two_events() {
        SpanData span = invoke(true, "triageAgent", "triageAgent");

        assertThat(span)
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "triageAgent");
        assertThat(span.getEvents())
                .extracting(EventData::getName)
                .containsExactly(GEN_AI_INVOKE_AGENT_EVENT, GEN_AI_INVOKE_AGENT_EVENT);
    }

    @Test
    @DisplayName("test_disabled_detection_does_nothing")
    void test_disabled_detection_does_nothing() {
        SpanData span = invoke(false, "triageAgent");

        assertThat(span)
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
        Assertions.assertThat(span.getEvents()).isEmpty();
    }

    private SpanData invoke(boolean enabled, String... agentNames) {
        exporter.reset();
        OTelAgentInvocationListener listener = new OTelAgentInvocationListener(enabled);
        Span span = tracer.spanBuilder("task.execute agentTask").startSpan();
        Context context = Context.current().with(span).with(FlowTaskSpanRef.KEY, new FlowTaskSpanRef(span));
        try (Scope ignored = context.makeCurrent()) {
            for (String agentName : agentNames) {
                listener.onAgentInvoked(agentName);
            }
        } finally {
            span.end();
        }
        return exporter.getFinishedSpanItems().stream().findFirst().orElseThrow();
    }
}
