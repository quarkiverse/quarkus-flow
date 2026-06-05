package io.quarkiverse.flow.runner.model;

import java.time.Instant;
import java.util.Map;

import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

public record ExecutionResponse(String instanceId,
        WorkflowStatus status,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> workflowOutput) {

    public static ExecutionResponse from(WorkflowInstance instance) {
        return new ExecutionResponse(instance.id(),
                instance.status(),
                instance.startedAt(),
                instance.completedAt(),
                getOutputIfCompleted(instance));
    }

    public static ExecutionResponse from(WorkflowInstance instance, WorkflowModel output) {
        return new ExecutionResponse(instance.id(),
                instance.status(),
                instance.startedAt(),
                instance.completedAt(),
                output.asMap().orElseGet(() -> Map.of("response", output.asJavaObject())));
    }

    private static Map<String, Object> getOutputIfCompleted(WorkflowInstance instance) {
        if (instance.status() == WorkflowStatus.COMPLETED ||
                instance.status() == WorkflowStatus.CANCELLED ||
                instance.status() == WorkflowStatus.FAULTED) {
            return instance.output() == null ? null
                    : instance.output().asMap().orElseGet(() -> Map.of("response", instance.output().asJavaObject()));
        }
        return null;
    }

}
