package io.quarkiverse.flow.providers;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

public record WorkflowTaskContext(WorkflowDefinitionId workflowId, String taskName, boolean isMicrometerSupported) {

    public WorkflowTaskContext(String workflowName, String taskName, boolean isMicrometerSupported) {
        this(new WorkflowDefinitionId("default", workflowName, "1.0.0"), taskName, isMicrometerSupported);
    }
}