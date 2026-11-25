package io.quarkiverse.flow.opentelemetry.it;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import io.opentelemetry.api.trace.Span;
import io.quarkiverse.flow.langchain4j.FlowAgentListener;

/**
 * Records the span that is current in each {@link FlowAgentListener} callback.
 */
@ApplicationScoped
public class SpanRecordingFlowAgentListener implements FlowAgentListener {

    public enum Callback {
        BEFORE,
        AFTER,
        ERROR
    }

    public record Invocation(Callback callback, String agentName, String spanId, boolean recording) {
    }

    private final List<Invocation> invocations = new CopyOnWriteArrayList<>();

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        record(Callback.BEFORE, request.agentName());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        record(Callback.AFTER, response.agentName());
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        record(Callback.ERROR, error.agentName());
    }

    public List<Invocation> invocations() {
        return List.copyOf(invocations);
    }

    public void reset() {
        invocations.clear();
    }

    private void record(Callback callback, String agentName) {
        Span span = Span.current();
        invocations.add(new Invocation(callback, agentName, span.getSpanContext().getSpanId(), span.isRecording()));
    }
}
