package io.quarkiverse.flow.lifecycle.ce;

import io.quarkiverse.flow.lifecycle.WorkflowApplicationIds;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.ce.TaskFailedCEData;

/**
 * {@link TaskFailedCEData} enriched with the {@code workflowApplicationId}.
 */
@RegisterForReflection(registerFullHierarchy = true)
public class FlowTaskFailedCEData extends TaskFailedCEData {

    private final String workflowApplicationId;

    public FlowTaskFailedCEData(TaskFailedEvent event) {
        super(event);
        this.workflowApplicationId = WorkflowApplicationIds.from(event);
    }

    public String getWorkflowApplicationId() {
        return workflowApplicationId;
    }
}
