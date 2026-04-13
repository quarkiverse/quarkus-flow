package io.quarkiverse.flow.structuredlogging;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.serverlessworkflow.impl.WorkflowDefinitionData;
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

    private final FlowStructuredLoggingConfig config;
    private final ObjectMapper objectMapper;

    public EventFormatter(FlowStructuredLoggingConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // Workflow Instance Events

    public String formatWorkflowStarted(WorkflowStartedEvent event) {
        Map<String, Object> json = baseWorkflowEvent("workflow.instance.started", event);
        WorkflowDefinitionData definition = event.workflowContext().definition();

        json.put("workflowNamespace", definition.workflow().getDocument().getNamespace());
        json.put("workflowName", definition.workflow().getDocument().getName());
        json.put("workflowVersion", definition.workflow().getDocument().getVersion());
        json.put("status", WorkflowStatus.RUNNING.name());
        json.put("startTime", event.eventDate());

        if (config.includeWorkflowPayloads()) {
            Object input = event.workflowContext().instanceData().input();
            json.put("input", handlePayload(input, "workflow.input"));
        }

        return toJson(json);
    }

    public String formatWorkflowCompleted(WorkflowCompletedEvent event) {
        Map<String, Object> json = baseWorkflowEvent("workflow.instance.completed", event);
        json.put("status", WorkflowStatus.COMPLETED.name());
        json.put("endTime", event.eventDate());

        if (config.includeWorkflowPayloads()) {
            Object output = event.workflowContext().instanceData().output();
            json.put("output", handlePayload(output, "workflow.output"));
        }

        return toJson(json);
    }

    public String formatWorkflowFailed(WorkflowFailedEvent event) {
        Map<String, Object> json = baseWorkflowEvent("workflow.instance.failed", event);
        json.put("status", WorkflowStatus.FAULTED.name());
        json.put("endTime", event.eventDate());

        if (config.includeErrorContext()) {
            Map<String, Object> error = new HashMap<>();
            if (event.cause() != null) {
                error.put("message", event.cause().getMessage());
                error.put("type", event.cause().getClass().getName());
                // Include first 10 stack trace elements
                StackTraceElement[] stackTrace = event.cause().getStackTrace();
                if (stackTrace != null && stackTrace.length > 0) {
                    StringBuilder stack = new StringBuilder();
                    for (int i = 0; i < Math.min(10, stackTrace.length); i++) {
                        stack.append(stackTrace[i].toString()).append("\n");
                    }
                    error.put("stackTrace", stack.toString());
                }
            }
            json.put("error", error);

            // Include workflow input for debugging context
            Object input = event.workflowContext().instanceData().input();
            json.put("input", handlePayload(input, "workflow.input"));
        }

        return toJson(json);
    }

    public String formatWorkflowCancelled(WorkflowCancelledEvent event) {
        Map<String, Object> json = baseWorkflowEvent("workflow.instance.cancelled", event);
        json.put("status", WorkflowStatus.CANCELLED.name());
        json.put("endTime", event.eventDate());
        return toJson(json);
    }

    public String formatWorkflowSuspended(WorkflowSuspendedEvent event) {
        Map<String, Object> json = baseWorkflowEvent("workflow.instance.suspended", event);
        json.put("status", WorkflowStatus.SUSPENDED.name());
        return toJson(json);
    }

    public String formatWorkflowResumed(WorkflowResumedEvent event) {
        Map<String, Object> json = baseWorkflowEvent("workflow.instance.resumed", event);
        json.put("status", WorkflowStatus.RUNNING.name());
        return toJson(json);
    }

    public String formatWorkflowStatusChanged(WorkflowStatusEvent event) {
        Map<String, Object> json = baseWorkflowEvent("workflow.instance.status.changed", event);
        json.put("status", event.workflowContext().instanceData().status().name());
        json.put("lastUpdateTime", event.eventDate());
        return toJson(json);
    }

    // Task Events

    public String formatTaskStarted(TaskStartedEvent event) {
        Map<String, Object> json = baseTaskEvent("workflow.task.started", event);
        json.put("status", "RUNNING");
        json.put("startTime", event.eventDate());

        if (config.includeTaskPayloads()) {
            Object input = event.taskContext().input();
            json.put("input", handlePayload(input, "task.input"));
        }

        return toJson(json);
    }

    public String formatTaskCompleted(TaskCompletedEvent event) {
        Map<String, Object> json = baseTaskEvent("workflow.task.completed", event);
        json.put("status", "COMPLETED");
        json.put("endTime", event.eventDate());

        if (config.includeTaskPayloads()) {
            Object output = event.taskContext().output();
            json.put("output", handlePayload(output, "task.output"));
        }

        return toJson(json);
    }

    public String formatTaskFailed(TaskFailedEvent event) {
        Map<String, Object> json = baseTaskEvent("workflow.task.failed", event);
        json.put("status", "FAILED");
        json.put("endTime", event.eventDate());

        if (config.includeErrorContext()) {
            Map<String, Object> error = new HashMap<>();
            if (event.cause() != null) {
                error.put("message", event.cause().getMessage());
                error.put("type", event.cause().getClass().getName());
            }
            json.put("error", error);

            // Always include input on failures
            Object input = event.taskContext().input();
            json.put("input", handlePayload(input, "task.input"));
        }

        return toJson(json);
    }

    public String formatTaskCancelled(TaskCancelledEvent event) {
        Map<String, Object> json = baseTaskEvent("workflow.task.cancelled", event);
        json.put("status", "CANCELLED");
        json.put("endTime", event.eventDate());
        return toJson(json);
    }

    public String formatTaskSuspended(TaskSuspendedEvent event) {
        Map<String, Object> json = baseTaskEvent("workflow.task.suspended", event);
        json.put("status", "SUSPENDED");
        return toJson(json);
    }

    public String formatTaskResumed(TaskResumedEvent event) {
        Map<String, Object> json = baseTaskEvent("workflow.task.resumed", event);
        json.put("status", "RUNNING");
        return toJson(json);
    }

    public String formatTaskRetried(TaskRetriedEvent event) {
        Map<String, Object> json = baseTaskEvent("workflow.task.retried", event);
        // Note: retry count not available in event, would need to track separately
        return toJson(json);
    }

    // Helper Methods

    private Map<String, Object> baseWorkflowEvent(String eventType, WorkflowEvent event) {
        Map<String, Object> json = new HashMap<>();
        json.put("eventType", eventType);
        json.put("timestamp", event.eventDate());
        json.put("instanceId", event.workflowContext().instanceData().id());
        return json;
    }

    private Map<String, Object> baseTaskEvent(String eventType, TaskEvent event) {
        Map<String, Object> json = baseWorkflowEvent(eventType, event);
        json.put("taskExecutionId", generateTaskExecutionId(event));
        json.put("taskName", event.taskContext().taskName());
        json.put("taskPosition", event.taskContext().position().jsonPointer());
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

    private Object handlePayload(Object payload, String fieldName) {
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
        truncated.put("__truncated__", true);
        truncated.put("__originalSize__", originalSize);

        int previewSize = Math.min(config.truncatePreviewSize(), json.length());
        truncated.put("__preview__", json.substring(0, previewSize));

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
