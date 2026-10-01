package io.quarkiverse.flow.lifecycle.ce;

import io.quarkiverse.flow.lifecycle.WorkflowApplicationIds;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.ce.TaskSuspendedCEData;

/**
 * {@link TaskSuspendedCEData} enriched with the {@code workflowApplicationId}.
 */
@RegisterForReflection(registerFullHierarchy = true)
public class FlowTaskSuspendedCEData extends TaskSuspendedCEData {

    private final String workflowApplicationId;

    public FlowTaskSuspendedCEData(TaskSuspendedEvent event) {
        super(event);
        this.workflowApplicationId = WorkflowApplicationIds.from(event);
    }

    public String getWorkflowApplicationId() {
        return workflowApplicationId;
    }
}
