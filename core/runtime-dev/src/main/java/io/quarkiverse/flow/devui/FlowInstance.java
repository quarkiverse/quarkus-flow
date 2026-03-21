package io.quarkiverse.flow.devui;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

public final class FlowInstance {

    private final String instanceId;
    private final String workflowNamespace;
    private final String workflowName;
    private final String workflowVersion;
    private final Instant startTime;
    private final WorkflowModel input;
    private WorkflowStatus status;
    private Instant lastUpdateTime;
    private Instant endTime;
    private String errorCode;
    private String errorMessage;
    private WorkflowModel output;
    private final List<LifecycleEventSummary> history;

    public FlowInstance(
            String instanceId,
            String workflowNamespace,
            String workflowName,
            String workflowVersion,
            WorkflowStatus status,
            Instant startTime,
            WorkflowModel input) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId is required");
        this.workflowNamespace = Objects.requireNonNull(workflowNamespace, "workflowNamespace is required");
        this.workflowName = Objects.requireNonNull(workflowName, "workflowName is required");
        this.workflowVersion = Objects.requireNonNull(workflowVersion, "workflowVersion is required");
        this.status = status;
        this.startTime = startTime;
        this.lastUpdateTime = startTime;
        this.input = input;
        this.history = new CopyOnWriteArrayList<>();
        this.history.add(new LifecycleEventSummary("workflow.started", null, startTime, null));
    }

    public void recordTaskFailure(String taskName, Instant timestamp, Throwable cause) {
        String errorMsg = cause != null ? cause.getMessage() : null;
        this.lastUpdateTime = timestamp;
        if (cause != null) {
            this.errorCode = cause.getClass().getSimpleName();
            this.errorMessage = errorMsg != null ? truncate(errorMsg) : null;
        }
        this.history.add(new LifecycleEventSummary("task.failed", taskName, timestamp, errorMsg));
    }

    public void recordCompletion(Instant timestamp, WorkflowModel output) {
        this.status = WorkflowStatus.COMPLETED;
        this.lastUpdateTime = timestamp;
        this.endTime = timestamp;
        this.output = output;
        this.history.add(new LifecycleEventSummary("workflow.completed", null, timestamp, null));
    }

    public void recordFailure(Instant timestamp, Throwable cause) {
        String errorMsg = cause != null ? cause.getMessage() : null;
        this.status = WorkflowStatus.FAULTED;
        this.lastUpdateTime = timestamp;
        this.endTime = timestamp;
        if (cause != null) {
            this.errorCode = cause.getClass().getSimpleName();
            this.errorMessage = errorMsg != null ? truncate(errorMsg) : null;
        }
        this.history.add(new LifecycleEventSummary("workflow.failed", null, timestamp, errorMsg));
    }

    public void recordCancellation(Instant timestamp) {
        this.status = WorkflowStatus.CANCELLED;
        this.lastUpdateTime = timestamp;
        this.endTime = timestamp;
        this.history.add(new LifecycleEventSummary("workflow.cancelled", null, timestamp, null));
    }

    public void recordSuspension(Instant timestamp) {
        this.status = WorkflowStatus.SUSPENDED;
        this.lastUpdateTime = timestamp;
        this.history.add(new LifecycleEventSummary("workflow.suspended", null, timestamp, null));
    }

    public void recordResumption(Instant timestamp) {
        this.status = WorkflowStatus.RUNNING;
        this.lastUpdateTime = timestamp;
        this.history.add(new LifecycleEventSummary("workflow.resumed", null, timestamp, null));
    }

    public void recordStatusChange(String details, Instant timestamp) {
        this.lastUpdateTime = timestamp;
        this.history.add(new LifecycleEventSummary("workflow.status.changed", null, timestamp, details));
    }

    public void recordTaskEvent(String eventType, String taskName, Instant timestamp) {
        this.lastUpdateTime = timestamp;
        this.history.add(new LifecycleEventSummary(eventType, taskName, timestamp, null));
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getWorkflowNamespace() {
        return workflowNamespace;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getWorkflowVersion() {
        return workflowVersion;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public WorkflowModel getInput() {
        return input;
    }

    public WorkflowModel getOutput() {
        return output;
    }

    public List<LifecycleEventSummary> getHistory() {
        return Collections.unmodifiableList(this.history);
    }

    private String truncate(String str) {
        if (str == null || str.length() <= 1024) {
            return str;
        }
        return str.substring(0, 1024) + "... (truncated)";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (FlowInstance) obj;
        return Objects.equals(this.instanceId, that.instanceId) &&
                Objects.equals(this.workflowNamespace, that.workflowNamespace) &&
                Objects.equals(this.workflowName, that.workflowName) &&
                Objects.equals(this.workflowVersion, that.workflowVersion) &&
                Objects.equals(this.status, that.status) &&
                Objects.equals(this.startTime, that.startTime) &&
                Objects.equals(this.lastUpdateTime, that.lastUpdateTime) &&
                Objects.equals(this.endTime, that.endTime) &&
                Objects.equals(this.errorCode, that.errorCode) &&
                Objects.equals(this.errorMessage, that.errorMessage) &&
                Objects.equals(this.input, that.input) &&
                Objects.equals(this.output, that.output) &&
                Objects.equals(this.history, that.history);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, workflowNamespace, workflowName, workflowVersion, status, startTime,
                lastUpdateTime, endTime, errorCode, errorMessage, input, output, history);
    }

    @Override
    public String toString() {
        return "FlowInstance[" +
                "instanceId=" + instanceId + ", " +
                "workflowNamespace=" + workflowNamespace + ", " +
                "workflowName=" + workflowName + ", " +
                "workflowVersion=" + workflowVersion + ", " +
                "status=" + status + ", " +
                "startTime=" + startTime + ", " +
                "lastUpdateTime=" + lastUpdateTime + ", " +
                "endTime=" + endTime + ", " +
                "errorCode=" + errorCode + ", " +
                "errorMessage=" + errorMessage + ", " +
                "input=" + input + ", " +
                "output=" + output + ", " +
                "history=" + history + ']';
    }

    public record LifecycleEventSummary(
            String type,
            String taskName,
            Instant timestamp,
            String details) {
    }
}
