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
    FAULT_TOLERANCE_TASK_RETRY("fault.tolerance.task.retry"),
    FAULT_TOLERANCE_TASK_FAILURE("fault.tolerance.task.failure");

    private final String metricName;

    FlowMetrics(String metricName) {
        this.metricName = metricName;
    }

    public String prefixedWith(String prefix) {
        return prefix + "." + this.metricName;
    }

}
