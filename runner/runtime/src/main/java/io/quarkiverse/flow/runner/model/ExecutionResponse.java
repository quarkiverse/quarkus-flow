package io.quarkiverse.flow.runner.model;

import java.time.Instant;

import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

@Deprecated
public record ExecutionResponse(
        String workflowApplicationId,
        String instanceId,
        WorkflowStatus status,
        Instant startedAt,
        Instant completedAt,
        Object workflowOutput) {

    public static ExecutionResponse from(String workflowApplicationId, WorkflowInstance instance, WorkflowModel model) {
        return new ExecutionResponse(workflowApplicationId,
                instance.id(),
                instance.status(),
                instance.startedAt(),
                instance.completedAt(), model.asJavaObject());
    }
}
