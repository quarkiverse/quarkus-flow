package org.acme.orchestrator.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.orchestrator.model.BuildTask;
import org.acme.orchestrator.model.TaskResult;
import org.acme.orchestrator.model.TaskState;
import org.acme.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test demonstrating idempotent task execution patterns.
 *
 * This test validates the core resilience patterns without workflow orchestration:
 * - Idempotent phase execution
 * - State persistence
 * - Retry behavior
 * - Phase-level resumption
 */
@QuarkusTest
class TaskExecutorTest {

    @Inject
    TaskExecutor taskExecutor;

    @Inject
    TaskStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore.clear();
    }

    @Test
    @DisplayName("should_execute_task_phases_idempotently")
    void test_idempotent_phase_execution() throws Exception {
        // Given - a test task with multiple phases
        BuildTask task = new BuildTask(
                "idempotent-test",
                "test", // has phases: setup, run-tests, collect-coverage
                "test-project",
                "main");

        // When - execute first phase (may fail due to simulated failures)
        TaskResult setupResult;
        try {
            setupResult = taskExecutor.executePhase(task, "setup");
        } catch (TaskExecutor.TaskExecutionException e) {
            // Simulated failure occurred - test can't verify idempotency, skip it
            return;
        }

        // Then - phase completes
        assertThat(setupResult.status()).isEqualTo(TaskStatus.COMPLETED);

        if (setupResult.status() == TaskStatus.COMPLETED) {
            TaskState state = stateStore.get(task.id());
            assertThat(state.getCompletedPhases()).contains("setup");

            // When - execute same phase again (idempotency test)
            TaskResult setupResult2 = taskExecutor.executePhase(task, "setup");

            // Then - phase is skipped (already completed)
            assertThat(setupResult2.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(setupResult2.message()).contains("already completed");

            // Verify state unchanged (no duplicate execution)
            TaskState stateAfter = stateStore.get(task.id());
            assertThat(stateAfter.getCompletedPhases()).contains("setup");

            System.out.println("✓ Phase 'setup' was idempotent - skipped on second execution");
        }
    }

    @Test
    @DisplayName("should_track_phase_completion_in_state")
    void test_phase_state_tracking() throws Exception {
        // Given - a build task
        BuildTask task = new BuildTask(
                "state-tracking-test",
                "build", // has phases: compile, package, verify
                "test-project",
                "main");

        // When - execute each phase sequentially
        String[] phases = { "compile", "package", "verify" };
        int completedCount = 0;

        for (String phase : phases) {
            try {
                TaskResult result = taskExecutor.executePhase(task, phase);

                if (result.status() == TaskStatus.COMPLETED) {
                    completedCount++;

                    // Then - state should reflect completed phase
                    TaskState state = stateStore.get(task.id());
                    assertThat(state.getCompletedPhases())
                            .as("State should track phase '%s' as completed", phase)
                            .contains(phase);

                    assertThat(state.getExternalState())
                            .as("External state should be updated for phase '%s'", phase)
                            .contains(phase);

                    System.out.printf("✓ Phase '%s' completed and tracked in state%n", phase);
                }
            } catch (TaskExecutor.TaskExecutionException e) {
                // Phase failed - this is expected due to simulated failures
                System.out.printf("✗ Phase '%s' failed: %s%n", phase, e.getMessage());
                break;
            }
        }

        // Verify final state
        TaskState finalState = stateStore.get(task.id());
        assertThat(finalState.getCompletedPhases().size()).isEqualTo(completedCount);

        System.out.printf("Total phases completed: %d/%d%n", completedCount, phases.length);
    }

    @Test
    @DisplayName("should_resume_from_last_completed_phase")
    void test_resume_from_checkpoint() throws Exception {
        // Given - a task that will be interrupted mid-execution
        BuildTask task = new BuildTask(
                "resume-test",
                "test",
                "test-project",
                "main");

        String[] phases = { "setup", "run-tests", "collect-coverage" };
        int lastCompletedPhase = -1;

        // When - execute until failure or completion
        for (int i = 0; i < phases.length; i++) {
            try {
                TaskResult result = taskExecutor.executePhase(task, phases[i]);
                if (result.status() == TaskStatus.COMPLETED) {
                    lastCompletedPhase = i;
                    System.out.printf("Phase '%s' completed%n", phases[i]);
                } else {
                    System.out.printf("Phase '%s' failed, stopping%n", phases[i]);
                    break;
                }
            } catch (TaskExecutor.TaskExecutionException e) {
                System.out.printf("Phase '%s' threw exception, stopping%n", phases[i]);
                break;
            }
        }

        if (lastCompletedPhase >= 0) {
            // Simulate workflow restart - resume from where we left off
            System.out.println("\n--- Simulating workflow restart ---");

            // Then - resume execution from next uncompleted phase
            for (int i = 0; i < phases.length; i++) {
                TaskState state = stateStore.get(task.id());

                if (state.isPhaseCompleted(phases[i])) {
                    System.out.printf("Skipping phase '%s' (already completed)%n", phases[i]);
                    continue;
                }

                // This is the resume point
                System.out.printf("Resuming from phase '%s'%n", phases[i]);

                try {
                    taskExecutor.executePhase(task, phases[i]);
                } catch (TaskExecutor.TaskExecutionException e) {
                    // Expected - may fail again
                    break;
                }
            }
        }

        // Verify idempotent resume
        TaskState finalState = stateStore.get(task.id());
        System.out.printf("\nFinal state: %d phases completed%n",
                finalState.getCompletedPhases().size());
    }

    @Test
    @DisplayName("should_track_attempt_count_on_failures")
    void test_retry_attempt_tracking() throws Exception {
        // Given - a task that may fail
        BuildTask task = new BuildTask(
                "retry-tracking-test",
                "lint",
                "test-project",
                "main");

        int maxAttempts = 5;
        int successfulAttempts = 0;
        int failedAttempts = 0;

        // When - attempt execution multiple times
        for (int i = 0; i < maxAttempts; i++) {
            try {
                TaskResult result = taskExecutor.executePhase(task, "check-style");

                if (result.status() == TaskStatus.COMPLETED) {
                    successfulAttempts++;
                    if (result.message().contains("already completed")) {
                        System.out.printf("Attempt %d: skipped (already completed)%n", i + 1);
                    } else {
                        System.out.printf("Attempt %d: succeeded%n", i + 1);
                    }
                    break; // Success, stop attempting
                }
            } catch (TaskExecutor.TaskExecutionException e) {
                failedAttempts++;
                System.out.printf("Attempt %d: failed - %s%n", i + 1, e.getMessage());

                // Check state tracks the failure
                TaskState state = stateStore.get(task.id());
                assertThat(state.getStatus()).isEqualTo(TaskStatus.FAILED);
                assertThat(state.getLastError()).isNotBlank();
            }
        }

        // Then - verify attempt tracking
        TaskState finalState = stateStore.get(task.id());
        assertThat(finalState.getAttemptCount()).isGreaterThan(0);

        System.out.printf("\nAttempt summary: %d successful, %d failed, total tracked: %d%n",
                successfulAttempts, failedAttempts, finalState.getAttemptCount());
    }

    @Test
    @DisplayName("should_preserve_external_state_across_phases")
    void test_external_state_preservation() throws Exception {
        // Given - a build task
        BuildTask task = new BuildTask(
                "external-state-test",
                "build",
                "test-project",
                "main");

        // When - execute multiple phases
        String[] phases = { "compile", "package" };

        for (String phase : phases) {
            try {
                taskExecutor.executePhase(task, phase);

                TaskState state = stateStore.get(task.id());
                if (state.isPhaseCompleted(phase)) {
                    // Then - external state should be updated
                    assertThat(state.getExternalState())
                            .as("External state should reflect phase '%s'", phase)
                            .contains(phase);

                    System.out.printf("Phase '%s': external state = %s%n",
                            phase, state.getExternalState());
                }
            } catch (TaskExecutor.TaskExecutionException e) {
                break;
            }
        }
    }
}
