package io.quarkiverse.flow.lifecycle.ce;

import io.quarkiverse.flow.lifecycle.WorkflowApplicationIds;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowFailedCEData;

/**
 * {@link WorkflowFailedCEData} enriched with the {@code workflowApplicationId}.
 */
@RegisterForReflection(registerFullHierarchy = true)
public class FlowWorkflowFailedCEData extends WorkflowFailedCEData {

    private final String workflowApplicationId;

    public FlowWorkflowFailedCEData(WorkflowFailedEvent event) {
        super(event);
        this.workflowApplicationId = WorkflowApplicationIds.from(event);
    }

    public String getWorkflowApplicationId() {
        return workflowApplicationId;
    }
}
