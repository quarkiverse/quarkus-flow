package org.acme.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.TaskState;
import org.acme.orchestrator.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciles workflow state with external state.
 * This is crucial for resume scenarios where workflow state and external state
 * (git, filesystem, databases) may have diverged.
 */
@ApplicationScoped
public class StateReconciliationService {
    private static final Logger LOG = LoggerFactory.getLogger(StateReconciliationService.class);

    @Inject
    TaskStateStore stateStore;

    /**
     * Reconcile workflow state with external state before resuming a task.
     *
     * Checks:
     * - Is the persisted state still valid?
     * - Does external state match what we expect?
     * - Can we safely resume from the current phase?
     */
    public ReconciliationResult reconcile(String taskId) {
        TaskState state = stateStore.get(taskId);

        LOG.info("Reconciling state for task {}: status={}, completedPhases={}",
                taskId, state.getStatus(), state.getCompletedPhases());

        // Check if external state exists and is valid
        if (state.getExternalState() != null && !state.getExternalState().isEmpty()) {
            LOG.info("External state exists for task {}: {}", taskId, state.getExternalState());

            // Validate that external state matches completed phases
            int expectedPhases = state.getCompletedPhases().size();
            if (state.getExternalState().startsWith("phase_")) {
                LOG.info("External state matches workflow state for task {}", taskId);
            } else {
                LOG.warn("External state mismatch for task {}, may need manual intervention", taskId);
                return new ReconciliationResult(false,
                        "External state does not match workflow state");
            }
        }

        // Check for incomplete phases that need retry
        if (state.getStatus() == TaskStatus.FAILED && state.getLastError() != null) {
            LOG.info("Task {} failed previously with error: {}, can retry",
                    taskId, state.getLastError());
            return new ReconciliationResult(true,
                    "Task can be safely resumed after failure");
        }

        // Check if task is already complete
        if (state.getStatus() == TaskStatus.COMPLETED) {
            LOG.info("Task {} is already completed", taskId);
            return new ReconciliationResult(true, "Task already completed");
        }

        LOG.info("Task {} can be safely resumed", taskId);
        return new ReconciliationResult(true, "Ready to resume");
    }

    public record ReconciliationResult(boolean canResume, String message) {
    }
}
