package io.quarkiverse.flow.providers;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

public record WorkflowTaskContext(WorkflowDefinitionId workflowId, String taskName) {

    public WorkflowTaskContext(String workflowName, String taskName) {
        this(new WorkflowDefinitionId("default", workflowName, "1.0.0"), taskName);
    }
}