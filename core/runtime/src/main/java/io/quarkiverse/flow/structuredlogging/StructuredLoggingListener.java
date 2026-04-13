package io.quarkiverse.flow.structuredlogging;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.serverlessworkflow.impl.lifecycle.TaskCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.TaskCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskFailedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskResumedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskRetriedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.TaskSuspendedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCancelledEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowCompletedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStatusEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;

/**
 * Workflow execution listener that emits structured JSON logs for all lifecycle events.
 * <p>
 * Events are logged to the {@code io.quarkiverse.flow.structuredlogging} logger category,
 * allowing users to configure separate log handlers for structured events.
 */
public class StructuredLoggingListener implements WorkflowExecutionListener {

    private static final Logger LOG = Logger.getLogger("io.quarkiverse.flow.structuredlogging");

    private final FlowStructuredLoggingConfig config;
    private final EventFormatter formatter;

    public StructuredLoggingListener(FlowStructuredLoggingConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.formatter = new EventFormatter(config, objectMapper);
    }

    // Workflow Instance Events

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        if (shouldLog("workflow.instance.started")) {
            log(formatter.formatWorkflowStarted(event));
        }
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (shouldLog("workflow.instance.completed")) {
            log(formatter.formatWorkflowCompleted(event));
        }
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        if (shouldLog("workflow.instance.failed")) {
            log(formatter.formatWorkflowFailed(event));
        }
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent event) {
        if (shouldLog("workflow.instance.cancelled")) {
            log(formatter.formatWorkflowCancelled(event));
        }
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent event) {
        if (shouldLog("workflow.instance.suspended")) {
            log(formatter.formatWorkflowSuspended(event));
        }
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent event) {
        if (shouldLog("workflow.instance.resumed")) {
            log(formatter.formatWorkflowResumed(event));
        }
    }

    @Override
    public void onWorkflowStatusChanged(WorkflowStatusEvent event) {
        if (shouldLog("workflow.instance.status.changed")) {
            log(formatter.formatWorkflowStatusChanged(event));
        }
    }

    // Task Events

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        if (shouldLog("workflow.task.started")) {
            log(formatter.formatTaskStarted(event));
        }
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (shouldLog("workflow.task.completed")) {
            log(formatter.formatTaskCompleted(event));
        }
    }

    @Override
    public void onTaskFailed(TaskFailedEvent event) {
        if (shouldLog("workflow.task.failed")) {
            log(formatter.formatTaskFailed(event));
        }
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent event) {
        if (shouldLog("workflow.task.cancelled")) {
            log(formatter.formatTaskCancelled(event));
        }
    }

    @Override
    public void onTaskSuspended(TaskSuspendedEvent event) {
        if (shouldLog("workflow.task.suspended")) {
            log(formatter.formatTaskSuspended(event));
        }
    }

    @Override
    public void onTaskResumed(TaskResumedEvent event) {
        if (shouldLog("workflow.task.resumed")) {
            log(formatter.formatTaskResumed(event));
        }
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent event) {
        if (shouldLog("workflow.task.retried")) {
            log(formatter.formatTaskRetried(event));
        }
    }

    // Helper Methods

    private boolean shouldLog(String eventType) {
        if (!config.enabled()) {
            return false;
        }

        // Check if event matches any configured patterns
        return config.events().stream()
                .anyMatch(pattern -> matchesPattern(eventType, pattern));
    }

    private boolean matchesPattern(String eventType, String pattern) {
        // Simple glob pattern matching
        // workflow.* matches all
        // workflow.instance.* matches workflow.instance.started, etc.
        // workflow.task.failed matches exact

        if (pattern.equals("*") || pattern.equals("workflow.*")) {
            return true;
        }

        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return eventType.startsWith(prefix + ".");
        }

        return eventType.equals(pattern);
    }

    private void log(String json) {
        // Log according to configured level
        switch (config.logLevel().toUpperCase()) {
            case "TRACE":
                LOG.trace(json);
                break;
            case "DEBUG":
                LOG.debug(json);
                break;
            case "WARN":
                LOG.warn(json);
                break;
            case "ERROR":
                LOG.error(json);
                break;
            case "INFO":
            default:
                LOG.info(json);
                break;
        }
    }
}
