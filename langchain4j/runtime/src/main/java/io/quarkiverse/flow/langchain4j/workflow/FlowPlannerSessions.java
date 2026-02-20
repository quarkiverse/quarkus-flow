package io.quarkiverse.flow.langchain4j.workflow;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolates {@link FlowPlanner} executions from the {@link io.serverlessworkflow.impl.WorkflowDefinition} instances.
 */
public final class FlowPlannerSessions {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPlannerSessions.class);

    private static final FlowPlannerSessions INSTANCE = new FlowPlannerSessions();
    private final ConcurrentMap<String, FlowPlanner> sessions = new ConcurrentHashMap<>();

    private FlowPlannerSessions() {
    }

    public static FlowPlannerSessions getInstance() {
        return INSTANCE;
    }

    public void open(String workflowInstanceId, FlowPlanner planner) {
        LOG.debug("Opening planner session for workflow instance {}", workflowInstanceId);
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

    // Begin: Tests access -------------------
    int activeSessionCount() {
        return sessions.size();
    }

    Set<String> activeSessionIds() {
        return Set.copyOf(sessions.keySet());
    }
    // End: test access ----------------------
}
