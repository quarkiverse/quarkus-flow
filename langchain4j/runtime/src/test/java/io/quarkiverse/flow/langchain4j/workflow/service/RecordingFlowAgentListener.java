package io.quarkiverse.flow.langchain4j.workflow.service;

import java.util.List;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import io.quarkiverse.flow.langchain4j.FlowAgentListener;

/**
 * Test-only {@link FlowAgentListener} appending each callback, as {@code "<callback> <agent id>"}, to a shared event
 * list, so tests can assert its order relative to the context being activated and closed.
 */
class RecordingFlowAgentListener implements FlowAgentListener {

    private final List<String> events;

    RecordingFlowAgentListener(List<String> events) {
        this.events = events;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        events.add("before " + request.agentId());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        events.add("after " + response.agentId());
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        events.add("error " + error.agentId());
    }
}
