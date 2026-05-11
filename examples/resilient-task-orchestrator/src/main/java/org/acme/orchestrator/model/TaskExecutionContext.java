package org.acme.orchestrator.model;

/**
 * Context for task execution - holds both the task definition and its execution result.
 * Used to pass data between workflow steps without requiring JsonNode manipulation.
 */
public record TaskExecutionContext(
        BuildTask task,
        TaskResult result) {
}
