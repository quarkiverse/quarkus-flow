package io.quarkiverse.flow.lifecycle.ce;

import io.quarkiverse.flow.lifecycle.WorkflowApplicationIds;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowCompletedCEData;

/**
 * {@link WorkflowCompletedCEData} enriched with the {@code workflowApplicationId}.
 */
@RegisterForReflection(registerFullHierarchy = true)
public class FlowWorkflowCompletedCEData extends WorkflowCompletedCEData {

    private final String workflowApplicationId;

    public FlowWorkflowCompletedCEData(WorkflowCompletedEvent event) {
        super(event);
        this.workflowApplicationId = WorkflowApplicationIds.from(event);
    }

    public String getWorkflowApplicationId() {
        return workflowApplicationId;
    }
}
