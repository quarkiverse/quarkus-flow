package org.acme.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a task execution.
 */
public record TaskResult(
        @JsonProperty("taskId") String taskId,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("message") String message,
        @JsonProperty("attemptNumber") int attemptNumber) {
}
