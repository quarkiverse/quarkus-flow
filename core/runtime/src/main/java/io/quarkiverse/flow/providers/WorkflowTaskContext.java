package io.quarkiverse.flow.providers;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

public record WorkflowTaskContext(WorkflowDefinitionId workflowId, String taskName) {

    public String workflowName() {
        return workflowId.name();
    }
}
