package io.quarkiverse.flow.runner.model;

import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;

public record StatusResponse(String instanceId, WorkflowStatus status) {

    public static StatusResponse from(WorkflowInstance instance) {
        return new StatusResponse(instance.id(), instance.status());
    }

}
