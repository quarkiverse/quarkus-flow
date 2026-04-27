package org.acme.orchestrator.model;

/**
 * Status of a task execution.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING
}
