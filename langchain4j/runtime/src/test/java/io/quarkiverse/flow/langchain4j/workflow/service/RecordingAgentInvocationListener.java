package io.quarkiverse.flow.langchain4j.workflow.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkiverse.flow.internal.FlowAgentInvocationListener;

/**
 * Test-only {@link FlowAgentInvocationListener}, registered through {@code META-INF/services} so
 * {@link FlowAgentInvocationListener#current()} resolves to it in this module's tests, recording every invocation for
 * assertions.
 */
public class RecordingAgentInvocationListener implements FlowAgentInvocationListener {

    static final List<String> INVOKED_AGENT_NAMES = new CopyOnWriteArrayList<>();

    @Override
    public void onAgentInvoked(String agentName) {
        INVOKED_AGENT_NAMES.add(agentName);
    }
}
