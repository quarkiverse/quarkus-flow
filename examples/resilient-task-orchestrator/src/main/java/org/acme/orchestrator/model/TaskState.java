package org.acme.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted state for a task that supports resume.
 * In a real system, this would be stored in a database.
 */
public class TaskState {
    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("status")
    private TaskStatus status;

    @JsonProperty("attemptCount")
    private int attemptCount;

    @JsonProperty("lastError")
    private String lastError;

    @JsonProperty("completedPhases")
    private List<String> completedPhases;

    @JsonProperty("externalState")
    private String externalState; // Simulates git state, filesystem state, etc.

    public TaskState() {
        this.completedPhases = new ArrayList<>();
        this.status = TaskStatus.PENDING;
        this.attemptCount = 0;
    }

    public TaskState(String taskId) {
        this();
        this.taskId = taskId;
    }

    // Getters and setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public List<String> getCompletedPhases() {
        return completedPhases;
    }

    public void setCompletedPhases(List<String> phases) {
        this.completedPhases = phases;
    }

    public void addCompletedPhase(String phase) {
        this.completedPhases.add(phase);
    }

    public boolean isPhaseCompleted(String phase) {
        return completedPhases.contains(phase);
    }

    public String getExternalState() {
        return externalState;
    }

    public void setExternalState(String externalState) {
        this.externalState = externalState;
    }
}
