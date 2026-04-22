package io.quarkiverse.flow.structuredlogging;

import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_INSTANCE_CANCELLED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_INSTANCE_COMPLETED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_INSTANCE_FAULTED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_INSTANCE_RESUMED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_INSTANCE_STARTED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_INSTANCE_STATUS_CHANGED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_INSTANCE_SUSPENDED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_TASK_CANCELLED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_TASK_COMPLETED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_TASK_FAULTED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_TASK_RESUMED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_TASK_RETRIED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_TASK_STARTED;
import static io.quarkiverse.flow.structuredlogging.StructuredLoggingEventTypes.WORKFLOW_TASK_SUSPENDED;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.TimestampFormat;
import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.WorkflowException;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

/**
 * Formats workflow lifecycle events as structured JSON logs.
 */
public class EventFormatter {

    // Task status constants (no TaskStatus enum in SDK, so we define them)
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    private static final String TASK_STATUS_FAILED = "FAILED";
    private static final String TASK_STATUS_CANCELLED = "CANCELLED";
    private static final String TASK_STATUS_SUSPENDED = "SUSPENDED";

    // JSON field name constants
    private static final String FIELD_EVENT_TYPE = "eventType";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_INSTANCE_ID = "instanceId";
    private static final String FIELD_TASK_EXECUTION_ID = "taskExecutionId";
    private static final String FIELD_TASK_NAME = "taskName";
    private static final String FIELD_TASK_POSITION = "taskPosition";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_START_TIME = "startTime";
    private static final String FIELD_END_TIME = "endTime";
    private static final String FIELD_LAST_UPDATE_TIME = "lastUpdateTime";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_OUTPUT = "output";
    // See https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#properties-53
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_ERROR_TITLE = "title";
    private static final String FIELD_ERROR_TYPE = "type";
    private static final String FIELD_ERROR_DETAIL = "detail";
    private static final String FIELD_ERROR_STATUS = "status";
    private static final String FIELD_ERROR_INSTANCE = "instance";
    private static final String FIELD_WORKFLOW_NAMESPACE = "workflowNamespace";
    private static final String FIELD_WORKFLOW_NAME = "workflowName";
    private static final String FIELD_WORKFLOW_VERSION = "workflowVersion";
    private static final String FIELD_TRUNCATED = "__truncated__";
    private static final String FIELD_ORIGINAL_SIZE = "__originalSize__";
    private static final String FIELD_PREVIEW = "__preview__";

    private final FlowStructuredLoggingConfig config;
    private final ObjectMapper objectMapper;

    public EventFormatter(FlowStructuredLoggingConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        // Validate custom pattern if CUSTOM format is selected
        if (config.timestampFormat() == TimestampFormat.CUSTOM) {
            if (config.timestampPattern().isEmpty()) {
                throw new IllegalArgumentException(
                        "quarkus.flow.structured-logging.timestamp-pattern must be set when timestamp-format is 'custom'");
            }
            try {
                DateTimeFormatter.ofPattern(config.timestampPattern().get());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid timestamp pattern '" + config.timestampPattern().get() + "': " + e.getMessage(),
                        e);
            }
        }
    }

    // Workflow Instance Events

    public String formatWorkflowStarted(WorkflowStartedEvent event) {
        Map<String, Object> json = baseWorkflowEvent(WORKFLOW_INSTANCE_STARTED, event);
        WorkflowDefinitionData definition = event.workflowContext().definition();

        json.put(FIELD_WORKFLOW_NAMESPACE, definition.workflow().getDocument().getNamespace());
        json.put(FIELD_WORKFLOW_NAME, definition.workflow().getDocument().getName());
        json.put(FIELD_WORKFLOW_VERSION, definition.workflow().getDocument().getVersion());
        json.put(FIELD_STATUS, WorkflowStatus.RUNNING.name());
        json.put(FIELD_START_TIME, formatTimestamp(event.eventDate()));

        if (config.includeWorkflowPayloads()) {
            Object input = event.workflowContext().instanceData().input();
            json.put(FIELD_INPUT, handlePayload(input));
        }

        return toJson(json);
    }

    public String formatWorkflowCompleted(WorkflowCompletedEvent event) {
        Map<String, Object> json = baseWorkflowEvent(WORKFLOW_INSTANCE_COMPLETED, event);
        json.put(FIELD_STATUS, WorkflowStatus.COMPLETED.name());
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));

        if (config.includeWorkflowPayloads())
            json.put(FIELD_OUTPUT, handlePayload(event.output()));

        return toJson(json);
    }

    public String formatWorkflowFailed(WorkflowFailedEvent event) {
        Map<String, Object> json = baseWorkflowEvent(WORKFLOW_INSTANCE_FAULTED, event);
        json.put(FIELD_STATUS, WorkflowStatus.FAULTED.name());
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));

        if (config.includeErrorContext()) {
            Map<String, Object> error = new HashMap<>();
            if (event.cause() != null) {
                if (event.cause() instanceof WorkflowException) {
                    final WorkflowError workflowError = ((WorkflowException) event.cause()).getWorkflowError();
                    error.put(FIELD_ERROR_TITLE, workflowError.title());
                    error.put(FIELD_ERROR_DETAIL, truncateErrorDetail(workflowError.details()));
                    error.put(FIELD_ERROR_INSTANCE, workflowError.instance());
                    error.put(FIELD_ERROR_STATUS, workflowError.status());
                    error.put(FIELD_ERROR_TYPE, workflowError.type());
                } else {
                    error.put(FIELD_ERROR_TITLE, event.cause().getMessage());
                    error.put(FIELD_ERROR_TYPE, event.cause().getClass().getName());
                    error.put(FIELD_ERROR_DETAIL, formatStackTrace(event.cause().getStackTrace()));
                }
            }
            json.put(FIELD_ERROR, error);

            // Include workflow input for debugging context
            Object input = event.workflowContext().instanceData().input();
            json.put(FIELD_INPUT, handlePayload(input));
        }

        return toJson(json);
    }

    private String formatStackTrace(StackTraceElement[] stackTrace) {
        if (stackTrace != null && stackTrace.length > 0) {
            StringBuilder stack = new StringBuilder();
            for (int i = 0; i < Math.min(config.stackTraceMaxLines(), stackTrace.length); i++) {
                stack.append(stackTrace[i].toString()).append("\n");
            }
            return stack.toString();
        }
        return "";
    }

    private String truncateErrorDetail(String detail) {
        if (detail == null || detail.isEmpty()) {
            return detail;
        }

        // Split by standard or carriage-return newlines
        String[] lines = detail.split("\r?\n");
        if (lines.length <= config.stackTraceMaxLines()) {
            return detail;
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < config.stackTraceMaxLines(); i++) {
            truncated.append(lines[i]).append("\n");
        }
        return truncated.toString();
    }

    public String formatWorkflowCancelled(WorkflowCancelledEvent event) {
        Map<String, Object> json = baseWorkflowEvent(WORKFLOW_INSTANCE_CANCELLED, event);
        json.put(FIELD_STATUS, WorkflowStatus.CANCELLED.name());
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
        return toJson(json);
    }

    public String formatWorkflowSuspended(WorkflowSuspendedEvent event) {
        Map<String, Object> json = baseWorkflowEvent(WORKFLOW_INSTANCE_SUSPENDED, event);
        json.put(FIELD_STATUS, WorkflowStatus.SUSPENDED.name());
        return toJson(json);
    }

    public String formatWorkflowResumed(WorkflowResumedEvent event) {
        Map<String, Object> json = baseWorkflowEvent(WORKFLOW_INSTANCE_RESUMED, event);
        json.put(FIELD_STATUS, WorkflowStatus.RUNNING.name());
        return toJson(json);
    }

    public String formatWorkflowStatusChanged(WorkflowStatusEvent event) {
        Map<String, Object> json = baseWorkflowEvent(WORKFLOW_INSTANCE_STATUS_CHANGED, event);
        json.put(FIELD_STATUS, event.workflowContext().instanceData().status().name());
        json.put(FIELD_LAST_UPDATE_TIME, formatTimestamp(event.eventDate()));
        return toJson(json);
    }

    // Task Events

    public String formatTaskStarted(TaskStartedEvent event) {
        Map<String, Object> json = baseTaskEvent(WORKFLOW_TASK_STARTED, event);
        json.put(FIELD_STATUS, TASK_STATUS_RUNNING);
        json.put(FIELD_START_TIME, formatTimestamp(event.eventDate()));

        if (config.includeTaskPayloads()) {
            Object input = event.taskContext().input();
            json.put(FIELD_INPUT, handlePayload(input));
        }

        return toJson(json);
    }

    public String formatTaskCompleted(TaskCompletedEvent event) {
        Map<String, Object> json = baseTaskEvent(WORKFLOW_TASK_COMPLETED, event);
        json.put(FIELD_STATUS, TASK_STATUS_COMPLETED);
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));

        if (config.includeTaskPayloads()) {
            Object output = event.taskContext().output();
            json.put(FIELD_OUTPUT, handlePayload(output));
        }

        return toJson(json);
    }

    public String formatTaskFailed(TaskFailedEvent event) {
        Map<String, Object> json = baseTaskEvent(WORKFLOW_TASK_FAULTED, event);
        json.put(FIELD_STATUS, TASK_STATUS_FAILED);
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));

        if (config.includeErrorContext()) {
            Map<String, Object> error = new HashMap<>();
            if (event.cause() != null) {
                error.put(FIELD_ERROR_TITLE, event.cause().getMessage());
                error.put(FIELD_ERROR_TYPE, event.cause().getClass().getName());
            }
            json.put(FIELD_ERROR, error);

            // Always include input on failures
            Object input = event.taskContext().input();
            json.put(FIELD_INPUT, handlePayload(input));
        }

        return toJson(json);
    }

    public String formatTaskCancelled(TaskCancelledEvent event) {
        Map<String, Object> json = baseTaskEvent(WORKFLOW_TASK_CANCELLED, event);
        json.put(FIELD_STATUS, TASK_STATUS_CANCELLED);
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
        return toJson(json);
    }

    public String formatTaskSuspended(TaskSuspendedEvent event) {
        Map<String, Object> json = baseTaskEvent(WORKFLOW_TASK_SUSPENDED, event);
        json.put(FIELD_STATUS, TASK_STATUS_SUSPENDED);
        return toJson(json);
    }

    public String formatTaskResumed(TaskResumedEvent event) {
        Map<String, Object> json = baseTaskEvent(WORKFLOW_TASK_RESUMED, event);
        json.put(FIELD_STATUS, TASK_STATUS_RUNNING);
        return toJson(json);
    }

    public String formatTaskRetried(TaskRetriedEvent event) {
        Map<String, Object> json = baseTaskEvent(WORKFLOW_TASK_RETRIED, event);
        // Note: retry count not available in event, would need to track separately
        return toJson(json);
    }

    // Helper Methods

    private Map<String, Object> baseWorkflowEvent(String filterKey, WorkflowEvent event) {
        Map<String, Object> json = new HashMap<>();
        // Use official CloudEvent type from SW specification
        json.put(FIELD_EVENT_TYPE, StructuredLoggingEventTypes.toCloudEventType(filterKey));
        json.put(FIELD_TIMESTAMP, formatTimestamp(event.eventDate()));
        json.put(FIELD_INSTANCE_ID, event.workflowContext().instanceData().id());
        return json;
    }

    private Map<String, Object> baseTaskEvent(String filterKey, TaskEvent event) {
        Map<String, Object> json = baseWorkflowEvent(filterKey, event);
        json.put(FIELD_TASK_EXECUTION_ID, generateTaskExecutionId(event));
        json.put(FIELD_TASK_NAME, event.taskContext().taskName());
        json.put(FIELD_TASK_POSITION, event.taskContext().position().jsonPointer());
        // Note: taskType inference would require parsing the workflow definition
        // For now, omit it or add in future enhancement
        return json;
    }

    private String generateTaskExecutionId(TaskEvent event) {
        // Generate deterministic ID based on instance + task position + timestamp
        String composite = event.workflowContext().instanceData().id() +
                ":" + event.taskContext().position().jsonPointer() +
                ":" + event.eventDate().toInstant().toEpochMilli();
        return UUID.nameUUIDFromBytes(composite.getBytes()).toString();
    }

    private Object formatTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null)
            return null;

        return switch (config.timestampFormat()) {
            case EPOCH_SECONDS -> {
                java.time.Instant instant = timestamp.toInstant();
                yield instant.getEpochSecond() + (instant.getNano() / 1_000_000_000.0);
            }
            case EPOCH_MILLIS -> timestamp.toInstant().toEpochMilli();
            case EPOCH_NANOS -> {
                java.time.Instant inst = timestamp.toInstant();
                yield inst.getEpochSecond() * 1_000_000_000L + inst.getNano();
            }
            case CUSTOM -> {
                // we already checked the optional in the constructor
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                        config.timestampPattern().get());
                yield timestamp.format(formatter);
            }
            default -> timestamp.toString();
        };
    }

    private Object handlePayload(Object payload) {
        if (payload == null) {
            return null;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            int size = json.getBytes().length;

            if (size <= config.payloadMaxSize()) {
                // Return as-is (will be serialized again, but keeps type info)
                return payload;
            } else {
                // Truncate
                return truncatePayload(json, size);
            }
        } catch (JsonProcessingException e) {
            // Fallback to toString if serialization fails
            String str = payload.toString();
            if (str.length() > config.payloadMaxSize()) {
                return truncatePayload(str, str.length());
            }
            return str;
        }
    }

    private Map<String, Object> truncatePayload(String json, int originalSize) {
        Map<String, Object> truncated = new HashMap<>();
        truncated.put(FIELD_TRUNCATED, true);
        truncated.put(FIELD_ORIGINAL_SIZE, originalSize);

        int previewSize = Math.min(config.truncatePreviewSize(), json.length());
        truncated.put(FIELD_PREVIEW, json.substring(0, previewSize));

        return truncated;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // Fallback to simple string representation
            return "{\"error\":\"Failed to serialize event: " + e.getMessage() + "\"}";
        }
    }
}
