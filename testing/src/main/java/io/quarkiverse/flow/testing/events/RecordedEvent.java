package io.quarkiverse.flow.testing.events;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

public class RecordedEvent {

    private final EventType type;
    private final Instant timestamp;
    private final WorkflowDefinitionId workflowId;
    private final String instanceId;
    private final Object originalEvent;
    private final Map<String, Object> metadata;

    private RecordedEvent(Builder builder) {
        this.type = builder.type;
        this.timestamp = builder.timestamp;
        this.workflowId = builder.workflowId;
        this.instanceId = builder.instanceId;
        this.originalEvent = builder.originalEvent;
        this.metadata = new HashMap<>(builder.metadata);
    }

    /**
     * Creates a RecordedWorkflowEvent from a WorkflowStartedEvent.
     */
    public static RecordedEvent from(WorkflowStartedEvent event) {
        return builder()
                .type(EventType.WORKFLOW_STARTED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("workflowName", event.workflowContext().definition().workflow().getDocument().getName())
                .addMetadata("workflowVersion",
                        event.workflowContext().definition().workflow().getDocument().getVersion())
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a WorkflowCompletedEvent.
     */
    public static RecordedEvent from(WorkflowCompletedEvent event) {
        return builder()
                .type(EventType.WORKFLOW_COMPLETED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("output", event.output())
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a WorkflowFailedEvent.
     */
    public static RecordedEvent from(WorkflowFailedEvent event) {
        return builder()
                .type(EventType.WORKFLOW_FAILED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("error", event.cause())
                .addMetadata("errorMessage", event.cause() != null ? event.cause().getMessage() : null)
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a WorkflowCancelledEvent.
     */
    public static RecordedEvent from(WorkflowCancelledEvent event) {
        return builder()
                .type(EventType.WORKFLOW_CANCELED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a WorkflowSuspendedEvent.
     */
    public static RecordedEvent from(WorkflowSuspendedEvent event) {
        return builder()
                .type(EventType.WORKFLOW_SUSPENDED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a WorkflowResumedEvent.
     */
    public static RecordedEvent from(WorkflowResumedEvent event) {
        return builder()
                .type(EventType.WORKFLOW_RESUMED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a TaskStartedEvent.
     */
    public static RecordedEvent from(TaskStartedEvent event) {
        return builder()
                .type(EventType.TASK_STARTED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("taskName", event.taskContext().taskName())
                .addMetadata("taskId", event.taskContext().position().jsonPointer())
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a TaskCompletedEvent.
     */
    public static RecordedEvent from(TaskCompletedEvent event) {
        return builder()
                .type(EventType.TASK_COMPLETED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("taskName", event.taskContext().taskName())
                .addMetadata("taskId", event.taskContext().position().jsonPointer())
                .addMetadata("output", event.taskContext().output())
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a TaskFailedEvent.
     */
    public static RecordedEvent from(TaskFailedEvent event) {
        return builder()
                .type(EventType.TASK_FAILED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("taskName", event.taskContext().taskName())
                .addMetadata("taskId", event.taskContext().position().jsonPointer())
                .addMetadata("error", event.cause())
                .addMetadata("errorMessage", event.cause() != null ? event.cause().getMessage() : null)
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a TaskCancelledEvent.
     */
    public static RecordedEvent from(TaskCancelledEvent event) {
        return builder()
                .type(EventType.TASK_CANCELLED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("taskName", event.taskContext().taskName())
                .addMetadata("taskId", event.taskContext().position().jsonPointer())
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a TaskSuspendedEvent.
     */
    public static RecordedEvent from(TaskSuspendedEvent event) {
        return builder()
                .type(EventType.TASK_SUSPENDED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("taskName", event.taskContext().taskName())
                .addMetadata("taskId", event.taskContext().position().jsonPointer())
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a TaskResumedEvent.
     */
    public static RecordedEvent from(TaskResumedEvent event) {
        return builder()
                .type(EventType.TASK_RESUMED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("taskName", event.taskContext().taskName())
                .addMetadata("taskId", event.taskContext().position().jsonPointer())
                .build();
    }

    /**
     * Creates a RecordedWorkflowEvent from a TaskRetriedEvent.
     */
    public static RecordedEvent from(TaskRetriedEvent event) {
        return builder()
                .type(EventType.TASK_RETRIED)
                .workflowId(event.workflowContext().definition().id())
                .instanceId(event.workflowContext().instanceData().id())
                .originalEvent(event)
                .addMetadata("taskName", event.taskContext().taskName())
                .addMetadata("taskId", event.taskContext().position().jsonPointer())
                .build();
    }

    // Getters

    public EventType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public WorkflowDefinitionId getWorkflowId() {
        return workflowId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Object getOriginalEvent() {
        return originalEvent;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    // Convenience methods for common metadata access

    public Optional<String> getTaskName() {
        return Optional.ofNullable((String) metadata.get("taskName"));
    }

    public Optional<String> getTaskId() {
        return Optional.ofNullable((String) metadata.get("taskId"));
    }

    public Optional<WorkflowModel> getOutput() {
        return Optional.ofNullable((WorkflowModel) metadata.get("output"));
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable((Throwable) metadata.get("error"));
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable((String) metadata.get("errorMessage"));
    }

    public boolean isWorkflowEvent() {
        return type.name().startsWith("WORKFLOW_");
    }

    public boolean isTaskEvent() {
        return type.name().startsWith("TASK_");
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EventType type;
        private Instant timestamp = Instant.now();
        private WorkflowDefinitionId workflowId;
        private String instanceId;
        private Object originalEvent;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder type(EventType type) {
            this.type = type;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder workflowId(WorkflowDefinitionId workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder originalEvent(Object originalEvent) {
            this.originalEvent = originalEvent;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public RecordedEvent build() {
            if (type == null) {
                throw new IllegalStateException("EventType is required");
            }
            if (workflowId == null) {
                throw new IllegalStateException("WorkflowId is required");
            }
            if (instanceId == null) {
                throw new IllegalStateException("InstanceId is required");
            }
            return new RecordedEvent(this);
        }
    }

    @Override
    public String toString() {
        return "RecordedWorkflowEvent{" +
                "type=" + type +
                ", timestamp=" + timestamp +
                ", workflowId='" + workflowId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", taskName=" + getTaskName().orElse("N/A") +
                '}';
    }
}