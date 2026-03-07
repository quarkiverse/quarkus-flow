package io.quarkiverse.flow.internal;

import java.time.Instant;
import java.util.List;

public record FlowInstance(
        String instanceId,
        String workflowNamespace,
        String workflowName,
        String workflowVersion,
        String status,
        Instant startTime,
        Instant lastUpdateTime,
        Instant endTime,
        String errorCode,
        String errorMessage,
        Object input,
        Object output,
        List<LifecycleEventSummary> history) {

    public record LifecycleEventSummary(
            String type,
            String taskName,
            Instant timestamp,
            String details) {
    }
}
