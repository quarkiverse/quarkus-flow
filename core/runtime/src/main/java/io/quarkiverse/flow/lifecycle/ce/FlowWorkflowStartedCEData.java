package io.quarkiverse.flow.lifecycle.ce;

import io.quarkiverse.flow.lifecycle.WorkflowApplicationIds;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowStartedCEData;

/**
 * {@link WorkflowStartedCEData} enriched with the {@code workflowApplicationId}.
 */
@RegisterForReflection(registerFullHierarchy = true)
public class FlowWorkflowStartedCEData extends WorkflowStartedCEData {

    private final String workflowApplicationId;

    public FlowWorkflowStartedCEData(WorkflowStartedEvent event) {
        super(event);
        this.workflowApplicationId = WorkflowApplicationIds.from(event);
    }

    public String getWorkflowApplicationId() {
        return workflowApplicationId;
    }
}
