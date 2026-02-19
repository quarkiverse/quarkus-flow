package io.quarkiverse.flow.langchain4j.workflow;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.InstanceHandle;

/**
 * Isolates {@link FlowPlanner} executions from the {@link io.serverlessworkflow.impl.WorkflowDefinition} instances.
 */
public final class FlowPlannerSessions {

    private static final Logger LOG = LoggerFactory.getLogger(FlowPlannerSessions.class);

    private static final FlowPlannerSessions INSTANCE = new FlowPlannerSessions();
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    private FlowPlannerSessions() {
    }

    ;

    public static FlowPlannerSessions getInstance() {
        return INSTANCE;
    }

    public void open(String workflowInstanceId, FlowPlanner planner, InstanceHandle<FlowPlanner> handle) {
        LOG.debug("Opening planner session for workflow instance {}", workflowInstanceId);
        sessions.putIfAbsent(workflowInstanceId, new Session(planner, handle));
    }

    public FlowPlanner get(String id) {
        final Session session = sessions.get(id);
        if (session != null) {
            return session.planner;
        }
        throw new IllegalArgumentException("Session with workflow instance id " + id + " not found");
    }

    public void close(String id, Throwable cause) {
        final Session session = sessions.remove(id);
        if (session != null) {
            LOG.debug("Closing planner session for workflow instance {}", id);
            if (cause != null)
                session.planner.abort(cause);
            else
                session.planner.finish();

            session.handle.destroy();
        }
    }

    public record Session(FlowPlanner planner, InstanceHandle<FlowPlanner> handle) {
    }
}
