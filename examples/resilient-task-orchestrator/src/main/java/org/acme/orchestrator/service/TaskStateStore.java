package org.acme.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.orchestrator.model.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for task state.
 * In a real system, this would be a database.
 */
@ApplicationScoped
public class TaskStateStore {
    private static final Logger LOG = LoggerFactory.getLogger(TaskStateStore.class);

    private final Map<String, TaskState> states = new ConcurrentHashMap<>();

    public TaskState get(String taskId) {
        return states.computeIfAbsent(taskId, TaskState::new);
    }

    public void save(TaskState state) {
        LOG.info("Persisting state for task {}: status={}, attempts={}, phases={}",
                state.getTaskId(), state.getStatus(), state.getAttemptCount(),
                state.getCompletedPhases());
        states.put(state.getTaskId(), state);
    }

    public void clear() {
        states.clear();
    }

    public Map<String, TaskState> getAll() {
        return Map.copyOf(states);
    }
}
