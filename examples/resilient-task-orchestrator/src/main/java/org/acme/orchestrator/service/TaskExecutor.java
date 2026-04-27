package org.acme.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.BuildTask;
import org.acme.orchestrator.model.TaskResult;
import org.acme.orchestrator.model.TaskState;
import org.acme.orchestrator.model.TaskStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Simulates task execution with configurable failure modes.
 * Demonstrates idempotent task execution that can be safely resumed.
 */
@ApplicationScoped
public class TaskExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExecutor.class);

    @Inject
    TaskStateStore stateStore;

    @ConfigProperty(name = "orchestrator.task.failure-rate", defaultValue = "0.3")
    double failureRate;

    @ConfigProperty(name = "orchestrator.task.delay-ms", defaultValue = "100")
    long delayMs;

    private final Random random = new Random();

    /**
     * Execute a task phase idempotently.
     * Checks if the phase was already completed before executing.
     */
    public TaskResult executePhase(BuildTask task, String phase) throws TaskExecutionException {
        TaskState state = stateStore.get(task.id());

        // Idempotency: skip if already completed
        if (state.isPhaseCompleted(phase)) {
            LOG.info("Phase '{}' already completed for task {}, skipping", phase, task.id());
            return new TaskResult(task.id(), TaskStatus.COMPLETED,
                    "Phase '" + phase + "' already completed", state.getAttemptCount());
        }

        state.incrementAttemptCount();
        state.setStatus(TaskStatus.RUNNING);
        stateStore.save(state);

        LOG.info("Executing phase '{}' for task {} (attempt {})", phase, task.id(), state.getAttemptCount());

        try {
            // Simulate work
            Thread.sleep(delayMs);

            // Simulate failures
            if (shouldFail()) {
                throw new TaskExecutionException("Simulated failure in phase '" + phase + "'");
            }

            // Update external state (simulates git commit, file creation, etc.)
            state.setExternalState("phase_" + phase + "_completed");

            // Mark phase complete
            state.addCompletedPhase(phase);
            state.setStatus(TaskStatus.COMPLETED);
            state.setLastError(null);
            stateStore.save(state);

            LOG.info("Phase '{}' completed for task {}", phase, task.id());
            return new TaskResult(task.id(), TaskStatus.COMPLETED,
                    "Phase '" + phase + "' completed successfully", state.getAttemptCount());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskExecutionException("Interrupted during phase '" + phase + "'", e);
        } catch (TaskExecutionException e) {
            state.setStatus(TaskStatus.FAILED);
            state.setLastError(e.getMessage());
            stateStore.save(state);
            throw e;
        }
    }

    /**
     * Execute an entire task with multiple phases.
     */
    public TaskResult executeTask(BuildTask task) throws TaskExecutionException {
        LOG.info("Starting execution of task: {} ({})", task.id(), task.name());

        // Example phases for different task types
        String[] phases = switch (task.name()) {
            case "lint" -> new String[] { "setup", "check-style", "report" };
            case "test" -> new String[] { "setup", "run-tests", "collect-coverage" };
            case "build" -> new String[] { "compile", "package", "verify" };
            case "deploy" -> new String[] { "prepare", "upload", "activate" };
            default -> new String[] { "execute" };
        };

        for (String phase : phases) {
            executePhase(task, phase);
        }

        return new TaskResult(task.id(), TaskStatus.COMPLETED,
                "All phases completed", stateStore.get(task.id()).getAttemptCount());
    }

    private boolean shouldFail() {
        return random.nextDouble() < failureRate;
    }

    public static class TaskExecutionException extends Exception {
        public TaskExecutionException(String message) {
            super(message);
        }

        public TaskExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
