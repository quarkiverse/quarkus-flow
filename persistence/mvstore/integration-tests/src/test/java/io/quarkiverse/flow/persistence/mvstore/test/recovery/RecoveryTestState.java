package io.quarkiverse.flow.persistence.mvstore.test.recovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;

final class RecoveryTestState {
    private static final Map<String, ConcurrentLinkedQueue<String>> STARTED = new ConcurrentHashMap<>();
    private static final Map<String, ConcurrentLinkedQueue<String>> COMPLETED = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> WORKFLOW_COMPLETED = new ConcurrentHashMap<>();

    private RecoveryTestState() {
    }

    static void recordTaskStarted(String phase, String taskName) {
        STARTED.computeIfAbsent(phase, k -> new ConcurrentLinkedQueue<>()).add(taskName);
    }

    static void recordTaskCompleted(String phase, String taskName) {
        COMPLETED.computeIfAbsent(phase, k -> new ConcurrentLinkedQueue<>()).add(taskName);
    }

    static void recordWorkflowCompleted(String phase) {
        WORKFLOW_COMPLETED.computeIfAbsent(phase, k -> new AtomicBoolean()).set(true);
    }

    static List<String> startedTasks(String phase) {
        return snapshot(STARTED.get(phase));
    }

    static List<String> completedTasks(String phase) {
        return snapshot(COMPLETED.get(phase));
    }

    static void awaitTasksCompleted(String phase, Duration timeout, String... taskNames) {
        awaitContains(COMPLETED.get(phase), timeout, taskNames);
    }

    static void awaitWorkflowCompleted(String phase, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isWorkflowCompleted(phase)) {
                return;
            }
            sleep(50);
        }
        Assertions.fail("Timed out waiting for workflow completion in phase " + phase);
    }

    static boolean isWorkflowCompleted(String phase) {
        AtomicBoolean completed = WORKFLOW_COMPLETED.get(phase);
        return completed != null && completed.get();
    }

    private static void awaitContains(ConcurrentLinkedQueue<String> queue, Duration timeout, String... taskNames) {
        Objects.requireNonNull(taskNames, "taskNames");
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (queue != null && queue.containsAll(List.of(taskNames))) {
                return;
            }
            sleep(50);
        }
        Assertions.fail("Timed out waiting for tasks " + List.of(taskNames));
    }

    private static List<String> snapshot(ConcurrentLinkedQueue<String> queue) {
        if (queue == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(queue);
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
