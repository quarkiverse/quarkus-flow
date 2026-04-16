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

import java.util.logging.Handler;

import org.jboss.logging.Logger;
import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.formatters.PatternFormatter;

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
 */
public class StructuredLoggingListener implements WorkflowExecutionListener {

    private static final String LOG_CATEGORY = "io.quarkiverse.flow.structuredlogging";
    private static final Logger LOG = Logger.getLogger(LOG_CATEGORY);

    private final FlowStructuredLoggingConfig config;
    private final EventFormatter formatter;

    // Volatile flag to ensure we only override the formatters once
    private volatile boolean formatterOverridden = false;

    public StructuredLoggingListener(FlowStructuredLoggingConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.formatter = new EventFormatter(config, objectMapper);
        // Note: No formatter logic in the constructor anymore!
    }

    // Workflow Instance Events

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        if (shouldLog(WORKFLOW_INSTANCE_STARTED)) {
            log(formatter.formatWorkflowStarted(event));
        }
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (shouldLog(WORKFLOW_INSTANCE_COMPLETED)) {
            log(formatter.formatWorkflowCompleted(event));
        }
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent event) {
        if (shouldLog(WORKFLOW_INSTANCE_FAULTED)) {
            log(formatter.formatWorkflowFailed(event));
        }
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent event) {
        if (shouldLog(WORKFLOW_INSTANCE_CANCELLED)) {
            log(formatter.formatWorkflowCancelled(event));
        }
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent event) {
        if (shouldLog(WORKFLOW_INSTANCE_SUSPENDED)) {
            log(formatter.formatWorkflowSuspended(event));
        }
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent event) {
        if (shouldLog(WORKFLOW_INSTANCE_RESUMED)) {
            log(formatter.formatWorkflowResumed(event));
        }
    }

    @Override
    public void onWorkflowStatusChanged(WorkflowStatusEvent event) {
        if (shouldLog(WORKFLOW_INSTANCE_STATUS_CHANGED)) {
            log(formatter.formatWorkflowStatusChanged(event));
        }
    }

    // Task Events

    @Override
    public void onTaskStarted(TaskStartedEvent event) {
        if (shouldLog(WORKFLOW_TASK_STARTED)) {
            log(formatter.formatTaskStarted(event));
        }
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent event) {
        if (shouldLog(WORKFLOW_TASK_COMPLETED)) {
            log(formatter.formatTaskCompleted(event));
        }
    }

    @Override
    public void onTaskFailed(TaskFailedEvent event) {
        if (shouldLog(WORKFLOW_TASK_FAULTED)) {
            log(formatter.formatTaskFailed(event));
        }
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent event) {
        if (shouldLog(WORKFLOW_TASK_CANCELLED)) {
            log(formatter.formatTaskCancelled(event));
        }
    }

    @Override
    public void onTaskSuspended(TaskSuspendedEvent event) {
        if (shouldLog(WORKFLOW_TASK_SUSPENDED)) {
            log(formatter.formatTaskSuspended(event));
        }
    }

    @Override
    public void onTaskResumed(TaskResumedEvent event) {
        if (shouldLog(WORKFLOW_TASK_RESUMED)) {
            log(formatter.formatTaskResumed(event));
        }
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent event) {
        if (shouldLog(WORKFLOW_TASK_RETRIED)) {
            log(formatter.formatTaskRetried(event));
        }
    }

    // Helper Methods

    private void overrideFormatterIfNeeded() {
        if (!formatterOverridden) {
            synchronized (this) {
                if (!formatterOverridden) {
                    java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(LOG_CATEGORY);
                    Handler[] handlers = julLogger.getHandlers();

                    if (handlers != null && handlers.length > 0) {
                        PatternFormatter pureJsonFormatter = new PatternFormatter("%s%n");
                        for (Handler handler : handlers) {
                            overrideHandlerFormatter(handler, pureJsonFormatter);
                        }
                    }

                    // Mark as complete. By the time the first workflow event hits,
                    // Quarkus logging is fully initialized.
                    formatterOverridden = true;
                }
            }
        }
    }

    private void overrideHandlerFormatter(Handler handler, java.util.logging.Formatter formatter) {
        handler.setFormatter(formatter);

        // Pierce through Quarkus's AsyncHandler wrapper to update the actual FileHandler inside
        if (handler instanceof ExtHandler) {
            Handler[] children = ((ExtHandler) handler).getHandlers();
            if (children != null) {
                for (Handler child : children) {
                    overrideHandlerFormatter(child, formatter);
                }
            }
        }
    }

    private boolean shouldLog(String eventType) {
        if (!config.enabled())
            return false;

        return config.events().stream()
                .anyMatch(pattern -> matchesPattern(eventType, pattern));
    }

    private boolean matchesPattern(String eventType, String pattern) {
        if (pattern.equals("*"))
            return true;

        if (pattern.endsWith(".*"))
            return eventType.startsWith(pattern.substring(0, pattern.length() - 2) + ".");

        return eventType.equals(pattern);
    }

    private void log(String json) {
        // Intercept right before logging to ensure handlers are firmly attached
        overrideFormatterIfNeeded();

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
        }
    }
}
