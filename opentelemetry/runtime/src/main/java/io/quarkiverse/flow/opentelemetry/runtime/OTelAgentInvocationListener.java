package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.GEN_AI_INVOKE_AGENT_EVENT;

import org.eclipse.microprofile.config.ConfigProvider;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.quarkiverse.flow.internal.FlowAgentInvocationListener;
import io.quarkiverse.flow.opentelemetry.runtime.config.FlowOTelConfig;

/**
 * Types the enclosing Flow task's {@code task.execute} span as a GenAI {@code invoke_agent} operation when a LangChain4j
 * agent is invoked while the task body runs, covering agent calls that are not made through the Java DSL's
 * {@code agent(..)} helper (see {@code SpanUtils#enrichAgentCall}, which handles that case at build time instead).
 * <p>
 * Controlled by {@code quarkus.flow.otel.genai.detect-agent-calls} (default {@code true}).
 */
public class OTelAgentInvocationListener implements FlowAgentInvocationListener {

    private final boolean enabled;

    public OTelAgentInvocationListener() {
        this(ConfigProvider.getConfig()
                .getOptionalValue(FlowOTelConfig.QUARKUS_FLOW_OTEL_GENAI_DETECT_AGENT_CALLS, Boolean.class)
                .orElse(true));
    }

    OTelAgentInvocationListener(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void onAgentInvoked(String agentName) {
        if (!enabled) {
            return;
        }

        FlowTaskSpanRef.of(Context.current()).ifPresent(ref -> enrich(ref, agentName));
    }

    private static void enrich(FlowTaskSpanRef ref, String agentName) {
        Span span = ref.span();
        span.addEvent(GEN_AI_INVOKE_AGENT_EVENT, Attributes.of(GEN_AI_AGENT_NAME, agentName));
        if (ref.claim(agentName)) {
            span.setAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                    .setAttribute(GEN_AI_AGENT_NAME, agentName);
        }
    }
}
