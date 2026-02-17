package io.quarkiverse.flow.providers;

public record WorkflowTaskContext(String workflowName, String taskName, boolean isMicrometerSupported) {
}
