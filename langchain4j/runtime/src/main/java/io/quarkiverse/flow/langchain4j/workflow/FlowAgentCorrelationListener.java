package io.quarkiverse.flow.langchain4j.workflow;

import java.util.Map;

import org.slf4j.MDC;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;

final class FlowAgentCorrelationListener implements AgentListener {

    private static final ThreadLocal<MdcSnapshot> SNAPSHOT = new ThreadLocal<>();

    private final AgentListener delegate;

    FlowAgentCorrelationListener(AgentListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        applyCorrelationFromScope(request.agenticScope());
        if (delegate != null) {
            delegate.beforeAgentInvocation(request);
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        try {
            if (delegate != null) {
                delegate.afterAgentInvocation(response);
            }
        } finally {
            restoreMdc();
        }
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        try {
            if (delegate != null) {
                delegate.onAgentInvocationError(error);
            }
        } finally {
            restoreMdc();
        }
    }

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) {
        if (delegate != null) {
            delegate.afterAgenticScopeCreated(scope);
        }
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) {
        if (delegate != null) {
            delegate.beforeAgenticScopeDestroyed(scope);
        }
    }

    @Override
    public void beforeToolExecution(BeforeToolExecution beforeToolExecution) {
        if (delegate != null) {
            delegate.beforeToolExecution(beforeToolExecution);
        }
    }

    @Override
    public void afterToolExecution(ToolExecution toolExecution) {
        if (delegate != null) {
            delegate.afterToolExecution(toolExecution);
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return delegate == null || delegate.inheritedBySubagents();
    }

    private static void applyCorrelationFromScope(AgenticScope scope) {
        if (scope == null) {
            SNAPSHOT.remove();
            return;
        }

        String instanceId = scope.readState(FlowAgentCorrelation.SCOPE_INSTANCE, null);
        String taskPos = scope.readState(FlowAgentCorrelation.SCOPE_TASK_POS, null);
        String taskName = scope.readState(FlowAgentCorrelation.SCOPE_TASK_NAME, null);

        boolean hasCorrelation = instanceId != null || taskPos != null || taskName != null;
        if (!hasCorrelation) {
            SNAPSHOT.remove();
            return;
        }

        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        SNAPSHOT.set(new MdcSnapshot(snapshot));

        if (instanceId != null) {
            MDC.put(FlowAgentCorrelation.MDC_INSTANCE, instanceId);
        } else {
            MDC.remove(FlowAgentCorrelation.MDC_INSTANCE);
        }
        if (taskPos != null) {
            MDC.put(FlowAgentCorrelation.MDC_TASK_POS, taskPos);
        } else {
            MDC.remove(FlowAgentCorrelation.MDC_TASK_POS);
        }
        if (taskName != null) {
            MDC.put(FlowAgentCorrelation.MDC_TASK_NAME, taskName);
        } else {
            MDC.remove(FlowAgentCorrelation.MDC_TASK_NAME);
        }
    }

    private static void restoreMdc() {
        MdcSnapshot snapshot = SNAPSHOT.get();
        if (snapshot == null) {
            return;
        }
        SNAPSHOT.remove();
        if (snapshot.map == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(snapshot.map);
        }
    }

    private static final class MdcSnapshot {
        private final Map<String, String> map;

        private MdcSnapshot(Map<String, String> map) {
            this.map = map;
        }
    }
}
