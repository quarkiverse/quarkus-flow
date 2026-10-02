package io.quarkiverse.flow.lifecycle.ce;

import io.quarkiverse.flow.lifecycle.WorkflowApplicationIds;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowSuspendedCEData;

/**
 * {@link WorkflowSuspendedCEData} enriched with the {@code workflowApplicationId}.
 */
@RegisterForReflection(registerFullHierarchy = true)
public class FlowWorkflowSuspendedCEData extends WorkflowSuspendedCEData {

    private final String workflowApplicationId;

    public FlowWorkflowSuspendedCEData(WorkflowSuspendedEvent event) {
        super(event);
        this.workflowApplicationId = WorkflowApplicationIds.from(event);
    }

    public String getWorkflowApplicationId() {
        return workflowApplicationId;
    }
}
