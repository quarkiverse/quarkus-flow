package io.quarkiverse.flow.opentelemetry.it;

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * With {@code quarkus.flow.otel.genai.detect-agent-calls=false}, a leaf agent invoked from a plain {@code function(..)}
 * task is not detected at runtime, so the task span is not typed as a GenAI {@code invoke_agent} operation.
 */
@QuarkusTest
@QuarkusTestResource(value = OllamaMockResource.class, restrictToAnnotatedClass = true)
@TestProfile(AgentCallDetectionDisabledIT.DetectionDisabledProfile.class)
class AgentCallDetectionDisabledIT {

    private static final String TASK_SPAN_PREFIX = "task.execute ";
    private static final String GEN_AI_INVOKE_AGENT_EVENT = "gen_ai.invoke_agent";

    @Inject
    LeafAgentCallFlow leafAgentCallFlow;

    @Inject
    InMemorySpanExporter exporter;

    @Test
    @DisplayName("test_leaf_agent_invoked_directly_is_not_typed_when_detection_is_disabled")
    void test_leaf_agent_invoked_directly_is_not_typed_when_detection_is_disabled() throws Exception {
        exporter.reset();
        leafAgentCallFlow.instance("My car was stolen.").start().get(30, TimeUnit.SECONDS);

        String taskSpanName = TASK_SPAN_PREFIX + LeafAgentCallFlow.TASK_NAME;
        await().atMost(Duration.ofSeconds(10))
                .until(() -> exporter.getFinishedSpanItems().stream()
                        .anyMatch(span -> taskSpanName.equals(span.getName())));

        SpanData taskSpan = exporter.getFinishedSpanItems().stream()
                .filter(span -> taskSpanName.equals(span.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(taskSpan.getAttributes().get(GEN_AI_OPERATION_NAME)).isNull();
        assertThat(taskSpan.getAttributes().get(GEN_AI_AGENT_NAME)).isNull();
        assertThat(taskSpan.getEvents())
                .noneMatch(event -> GEN_AI_INVOKE_AGENT_EVENT.equals(event.getName()));
    }

    public static class DetectionDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.flow.otel.genai.detect-agent-calls", "false");
        }
    }
}
