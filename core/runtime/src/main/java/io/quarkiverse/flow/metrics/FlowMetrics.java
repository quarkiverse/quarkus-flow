package io.quarkiverse.flow.metrics;

public enum FlowMetrics {

    WORKFLOW_STARTED_TOTAL("workflow.started.total"),
    WORKFLOW_COMPLETED_TOTAL("workflow.completed.total"),
    WORKFLOW_FAULTED_TOTAL("workflow.faulted.total"),
    WORKFLOW_CANCELLED_TOTAL("workflow.cancelled.total"),
    TASK_STARTED_TOTAL("task.started.total"),
    TASK_COMPLETED_TOTAL("task.completed.total"),
    TASK_RETRIES_TOTAL("task.retries.total"),
    TASK_FAILED_TOTAL("task.failed.total"),
    WORKFLOW_DURATION("workflow.duration"),
    TASK_DURATION("task.duration"),
    INSTANCES_RUNNING("instance.running"),
    INSTANCES_WAITING("instance.waiting"),
    INSTANCES_SUSPENDED("instance.suspended"),
    FAULT_TOLERANCE_TASK_RETRY_TOTAL("fault.tolerance.task.retry.total"),
    FAULT_TOLERANCE_TASK_RETRY_FAILURE_TOTAL("fault.tolerance.task.retry.failure.total"),
    FAULT_TOLERANCE_CIRCUIT_BREAKER_OPEN("fault.tolerance.circuit.breaker.open"),
    FAULT_TOLERANCE_CIRCUIT_BREAKER_HALF_OPEN("fault.tolerance.circuit.breaker.half.open"),
    FAULT_TOLERANCE_CIRCUIT_BREAKER_CLOSED("fault.tolerance.circuit.breaker.closed"),
    FAULT_TOLERANCE_CIRCUIT_BREAKER_PREVENTED_TOTAL("fault.tolerance.circuit.breaker.prevented.total"),
    FAULT_TOLERANCE_CIRCUIT_BREAKER_FAILURE_TOTAL("fault.tolerance.circuit.breaker.failure.total");

    private final String metricName;

    FlowMetrics(String metricName) {
        this.metricName = metricName;
    }

    public String prefixedWith(String prefix) {
        return prefix + "." + this.metricName;
    }

}
