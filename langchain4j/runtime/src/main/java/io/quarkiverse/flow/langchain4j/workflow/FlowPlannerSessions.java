package io.quarkiverse.flow.langchain4j.workflow;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Isolates {@link FlowPlanner} executions from the {@link io.serverlessworkflow.impl.WorkflowDefinition} instances.
 */
public enum FlowPlannerSessions {

    INSTANCE;

    final String SESSION_ID = "__flow_session_id";
    final ConcurrentHashMap<Object, FlowPlanner> sessions = new ConcurrentHashMap<>();

    CompletableFuture<Void> execute(AgenticScope scope, AgentInstance agent) {
        String id = getSessionId(scope);
        if (id == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Missing " + SESSION_ID + " in scope"));
        }
        FlowPlanner planner = sessions.get(id);
        if (planner == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No FlowPlanner for sessionId=" + id));
        }
        return planner.executeAgent(agent);

    }

    String register(AgenticScope scope, FlowPlanner planner) {
        final String sessionId = UUID.randomUUID().toString();
        scope.writeState(SESSION_ID, sessionId);
        this.sessions.put(sessionId, planner);
        return sessionId;
    }

    void unregister(String sessionId, FlowPlanner planner) {
        this.sessions.remove(sessionId, planner);
    }

    String getSessionId(AgenticScope scope) {
        return (String) scope.readState(SESSION_ID);
    }

}
