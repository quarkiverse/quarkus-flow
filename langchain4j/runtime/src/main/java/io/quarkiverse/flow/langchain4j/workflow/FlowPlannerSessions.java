package io.quarkiverse.flow.langchain4j.workflow;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Isolates {@link FlowPlanner} executions from the {@link io.serverlessworkflow.impl.WorkflowDefinition} instances.
 */
public final class FlowPlannerSessions {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPlannerSessions.class);

    private static final String FLOW_INSTANCE_ID = "__flow_instance_id__";
    private static final FlowPlannerSessions INSTANCE = new FlowPlannerSessions();
    private final ConcurrentMap<String, FlowPlanner> sessions = new ConcurrentHashMap<>();

    private FlowPlannerSessions() {
    }

    public static FlowPlannerSessions getInstance() {
        return INSTANCE;
    }

    public void open(String workflowInstanceId, FlowPlanner planner, AgenticScope scope) {
        LOG.debug("Opening planner session for workflow instance {}", workflowInstanceId);
        scope.writeState(FLOW_INSTANCE_ID, workflowInstanceId);
        sessions.putIfAbsent(workflowInstanceId, planner);
    }

    public FlowPlanner get(String id) {
        final FlowPlanner planner = sessions.get(id);
        if (planner != null) {
            return planner;
        }
        throw new IllegalArgumentException("Session with workflow instance id " + id + " not found");
    }

    public void close(String id, Throwable cause) {
        final FlowPlanner planner = sessions.remove(id);
        if (planner != null) {
            planner.doTermination(cause);
            planner.close();
            LOG.debug("Closed planner session for workflow instance {}", id);
        }
    }

    public void close(AgenticScope scope, Throwable cause) {
        final String workflowInstanceId = scope.readState(FLOW_INSTANCE_ID, "");
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            throw new IllegalArgumentException("Session with workflow instance id " + workflowInstanceId + " not found", cause);
        }
        this.close(workflowInstanceId, cause);
    }

    // Begin: Tests access -------------------
    int activeSessionCount() {
        return sessions.size();
    }

    Set<String> activeSessionIds() {
        return Set.copyOf(sessions.keySet());
    }
    // End: test access ----------------------
}
