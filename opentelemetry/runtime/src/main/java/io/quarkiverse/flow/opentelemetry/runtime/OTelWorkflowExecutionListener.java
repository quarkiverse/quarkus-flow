package io.quarkiverse.flow.opentelemetry.runtime;

import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.appendTaskEvent;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.appendWorkflowEvent;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.generateTaskSpanName;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.generateWorkflowSpanName;
import static io.quarkiverse.flow.opentelemetry.runtime.TaskEventType.TASK_CANCELLED;
import static io.quarkiverse.flow.opentelemetry.runtime.TaskEventType.TASK_COMPLETED;
import static io.quarkiverse.flow.opentelemetry.runtime.TaskEventType.TASK_RESUMED;
import static io.quarkiverse.flow.opentelemetry.runtime.TaskEventType.TASK_SUSPENDED;
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowEventType.WORKFLOW_CANCELLED;
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowEventType.WORKFLOW_COMPLETED;
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowEventType.WORKFLOW_RESUMED;
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowEventType.WORKFLOW_SUSPENDED;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkiverse.flow.opentelemetry.runtime.config.FlowOTelConfig;
import io.serverlessworkflow.api.types.RunTask;
import io.serverlessworkflow.api.types.RunTaskConfigurationUnion;
import io.serverlessworkflow.impl.TaskContextData;
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
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowFailedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowResumedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowSuspendedEvent;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxThread;

@ApplicationScoped
public class OTelWorkflowExecutionListener implements WorkflowExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OTelWorkflowExecutionListener.class);

    @ConfigProperty(name = "quarkus.flow.otel.task.name-strategy", defaultValue = "action-and-task-name")
    SpanUtils.TaskNameStrategy taskNameStrategy;

    @Inject
    SpanBuilderFactory spanBuilderFactory;

    @Inject
    InstrumentationContextManager contextManager;

    @Inject
    FlowOTelConfig oTelConfig;

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
        if (!oTelConfig.isEnabled()) {
            return;
        }
        WorkflowEventInfo eventInfo = WorkflowEventInfo.from(ev);
        logWorkflowEvent(eventInfo);
        printThreadAndCurrentVertxContext("On - " + eventInfo.eventType() + ", workflowName: " + eventInfo.wfName());

        Context parentContext = Context.current();
        String workflowSpanName = generateWorkflowSpanName(eventInfo.wfName());

        Span startSpan = spanBuilderFactory.newWorkflowSpan(workflowSpanName,
                eventInfo.wfApplicationId(),
                eventInfo.wfNamespace(),
                eventInfo.wfName(),
                eventInfo.wfInstanceId(),
                eventInfo.wfVersion(),
                parentContext).startSpan();
        appendWorkflowEvent(startSpan, eventInfo.eventType());

        InstrumentationContext workflowContext = InstrumentationContext.newBuilder()
                .parentContext(parentContext)
                .withStartSpan(startSpan)
                .withStartTime(Instant.now())
                .build();

        contextManager.putWorkflowInstanceContext(eventInfo.wfInstanceId(), workflowContext);
    }

    @Override
    public void onWorkflowSuspended(WorkflowSuspendedEvent ev) {
        doWorkflowEvent(ev);
    }

    @Override
    public void onWorkflowResumed(WorkflowResumedEvent ev) {
        doWorkflowEvent(ev);
    }

    @Override
    public void onWorkflowCompleted(WorkflowCompletedEvent ev) {
        doWorkflowEvent(ev);
    }

    @Override
    public void onWorkflowCancelled(WorkflowCancelledEvent ev) {
        doWorkflowEvent(ev);
    }

    @Override
    public void onWorkflowFailed(WorkflowFailedEvent ev) {
        doWorkflowEvent(ev);
    }

    private void doWorkflowEvent(WorkflowEvent ev) {
        if (!oTelConfig.isEnabled()) {
            return;
        }
        WorkflowEventInfo eventInfo = WorkflowEventInfo.from(ev);
        logWorkflowEvent(eventInfo);
        printThreadAndCurrentVertxContext("On - " + eventInfo.eventType() + ", workflowName: " + eventInfo.wfName());

        InstrumentationContext workflowContext = contextManager.getWorkflowInstanceContext(eventInfo.wfInstanceId());
        if (workflowContext == null) {
            LOGGER.warn("On - " + eventInfo.eventType() + ": no instrumentation context was found for "
                    + " workflowApplicationId: " + eventInfo.wfApplicationId()
                    + ", workflowNamespace: " + eventInfo.wfNamespace() + ", workflowName: " + eventInfo.wfName()
                    + ", workflowInstanceId: " + eventInfo.wfInstanceId() + ", workflowVersion: " + eventInfo.wfVersion());
            return;
        }

        Span startSpan = workflowContext.getStartSpan();
        if (eventInfo.eventType() == WORKFLOW_SUSPENDED || eventInfo.eventType() == WORKFLOW_RESUMED) {
            appendWorkflowEvent(workflowContext.getStartSpan(), eventInfo.eventType());
        } else if (eventInfo.eventType() == WORKFLOW_COMPLETED || eventInfo.eventType() == WORKFLOW_CANCELLED) {
            startSpan.setStatus(StatusCode.OK);
            contextManager.ensureAllTaskSpansAreClosed(eventInfo.wfInstanceId());
            appendWorkflowEvent(startSpan, eventInfo.eventType());
            startSpan.end();
            contextManager.removeWorkflowInstanceContext(eventInfo.wfInstanceId());
        } else {
            WorkflowFailedEvent failedEvent = (WorkflowFailedEvent) ev;
            startSpan.recordException(failedEvent.cause());
            startSpan.setStatus(StatusCode.ERROR, failedEvent.cause().getMessage());
            contextManager.ensureAllTaskSpansAreClosed(eventInfo.wfInstanceId());
            appendWorkflowEvent(startSpan, eventInfo.eventType());
            startSpan.end();
            contextManager.removeWorkflowInstanceContext(eventInfo.wfInstanceId());
        }
    }

    @Override
    public void onTaskStarted(TaskStartedEvent ev) {
        doTaskStartedOrRetried(ev);
    }

    @Override
    public void onTaskRetried(TaskRetriedEvent ev) {
        doTaskStartedOrRetried(ev);
    }

    private void doTaskStartedOrRetried(TaskEvent ev) {
        if (!oTelConfig.isEnabled()) {
            return;
        }
        TaskEventInfo eventInfo = TaskEventInfo.from(ev);
        logTaskEvent(eventInfo);
        printThreadAndCurrentVertxContext(
                "On - " + eventInfo.eventType() + ", workflowName: " + eventInfo.wfName() + ", taskName: "
                        + eventInfo.taskName());

        InstrumentationContext parentTaskContext = contextManager.findEnclosingParentContext(eventInfo.wfInstanceId(),
                eventInfo.taskId());
        Context parentContext = parentTaskContext.getStartSpan().storeInContext(parentTaskContext.getParentContext());
        String spanName = generateTaskSpanName(taskNameStrategy, eventInfo.taskId(), eventInfo.taskName(),
                eventInfo.taskInstanceIteration(), eventInfo.taskInstanceRetryAttempt());

        Span startSpan = spanBuilderFactory.newTaskSpan(spanName,
                eventInfo.wfApplicationId(),
                eventInfo.wfNamespace(),
                eventInfo.wfName(),
                eventInfo.wfInstanceId(),
                eventInfo.wfVersion(),
                eventInfo.taskId(),
                eventInfo.taskType().name(),
                eventInfo.taskName(),
                eventInfo.taskInstanceIteration(),
                eventInfo.taskInstanceRetrying(),
                eventInfo.taskInstanceRetryAttempt(),
                parentContext)
                .startSpan();

        appendTaskEvent(startSpan, eventInfo.eventType());

        Scope startSpanScope = null;
        if (requiresPropagation(eventInfo.taskType(), ev.taskContext())) {
            startSpanScope = startSpan.makeCurrent();
        }

        InstrumentationContext taskInstanceContext = InstrumentationContext.newBuilder()
                .withJsonPosition(eventInfo.taskId())
                .withTaskType(eventInfo.taskType())
                .withContainerPosition(containerContextPosition(eventInfo.taskType(), eventInfo.taskId()))
                .withStartSpan(startSpan)
                .withStartSpanScope(startSpanScope)
                .withStartTime(Instant.now())
                .parentContext(parentContext)
                .withIteration(eventInfo.taskInstanceIteration())
                .withRetrying(eventInfo.taskInstanceRetrying())
                .withRetryAttempt(eventInfo.taskInstanceRetryAttempt())
                .build();

        contextManager.putTaskInstanceInstanceContext(eventInfo.wfInstanceId(), eventInfo.taskId(),
                eventInfo.taskInstanceIteration(),
                eventInfo.taskInstanceRetryAttempt(), taskInstanceContext);
    }

    @Override
    public void onTaskCompleted(TaskCompletedEvent ev) {
        doTaskEvent(ev);
    }

    @Override
    public void onTaskCancelled(TaskCancelledEvent ev) {
        doTaskEvent(ev);
    }

    @Override
    public void onTaskFailed(TaskFailedEvent ev) {
        doTaskEvent(ev);
    }

    @Override
    public void onTaskSuspended(TaskSuspendedEvent ev) {
        doTaskEvent(ev);
    }

    @Override
    public void onTaskResumed(TaskResumedEvent ev) {
        doTaskEvent(ev);
    }

    private void doTaskEvent(TaskEvent ev) {
        if (!oTelConfig.isEnabled()) {
            return;
        }
        TaskEventInfo eventInfo = TaskEventInfo.from(ev);
        logTaskEvent(eventInfo);
        printThreadAndCurrentVertxContext(
                "On - " + eventInfo.eventType() + ", workflowName: " + eventInfo.wfName() + ", taskName: "
                        + eventInfo.taskName());

        InstrumentationContext taskInstanceContext = contextManager.getTaskInstanceContext(eventInfo.wfInstanceId(),
                eventInfo.taskId(),
                eventInfo.taskInstanceIteration(), eventInfo.taskInstanceRetryAttempt());
        if (taskInstanceContext == null) {
            LOGGER.warn("No taskInstanceContext was found for taskType: " + eventInfo.taskType() + ", taskName: "
                    + eventInfo.taskName() + " taskId: " + eventInfo.taskId()
                    + " iteration: " + eventInfo.taskInstanceIteration() + " isRetrying: " + eventInfo.taskInstanceRetrying()
                    + " retryAttempt: " + eventInfo.taskInstanceRetryAttempt());
            return;
        }

        if (TASK_CANCELLED == eventInfo.eventType() || TASK_COMPLETED == eventInfo.eventType()) {
            Span startSpan = taskInstanceContext.getStartSpan();
            if (taskInstanceContext.getStartSpanScope() != null) {
                taskInstanceContext.getStartSpanScope().close();
            }
            appendTaskEvent(startSpan, eventInfo.eventType());
            startSpan.setStatus(StatusCode.OK);
            startSpan.end();
            contextManager.removeTaskInstanceInstanceContext(eventInfo.wfInstanceId(), eventInfo.taskId(),
                    eventInfo.taskInstanceIteration(),
                    eventInfo.taskInstanceRetryAttempt());
        } else if (TASK_SUSPENDED == eventInfo.eventType() || TASK_RESUMED == eventInfo.eventType()) {
            Span startSpan = taskInstanceContext.getStartSpan();
            appendTaskEvent(startSpan, eventInfo.eventType());
        } else {
            Span startSpan = taskInstanceContext.getStartSpan();
            if (taskInstanceContext.getStartSpanScope() != null) {
                taskInstanceContext.getStartSpanScope().close();
            }
            TaskFailedEvent failedEvent = (TaskFailedEvent) ev;
            appendTaskEvent(startSpan, eventInfo.eventType());
            startSpan.recordException(failedEvent.cause());
            startSpan.setStatus(StatusCode.ERROR, failedEvent.cause().getMessage());
            startSpan.end();
            contextManager.removeTaskInstanceInstanceContext(eventInfo.wfInstanceId(), eventInfo.taskId(),
                    eventInfo.taskInstanceIteration(),
                    eventInfo.taskInstanceRetryAttempt());
        }
    }

    @Override
    public void close() {
        WorkflowExecutionListener.super.close();
    }

    private static boolean requiresPropagation(TaskType taskType, TaskContextData taskContextData) {
        if (taskType == TaskType.CALL_HTTP) {
            return true;
        }
        if (taskType == TaskType.RUN) {
            RunTaskConfigurationUnion runTaskConfig = ((RunTask) taskContextData.task()).getRun();
            return runTaskConfig.getRunWorkflow() != null;
        }
        return false;
    }

    private static String containerContextPosition(TaskType taskType, String jsonPosition) {
        switch (taskType) {
            case DO:
            case FOR:
            case LISTEN:
                // e.g.
                // do/0/doTask/do
                // do/0/forTask/do
                // do/0/listenTask/do
                return jsonPosition.substring(0, jsonPosition.length() - 3);
            case TRY:
                // e.g
                // do/0/tryTask/try
                return jsonPosition.substring(0, jsonPosition.length() - 4);
            case FORK:
                // e.g.
                // do/0/forkTask/branch
                return jsonPosition.substring(0, jsonPosition.length() - 7);
            default:
                return null;
        }
    }

    private static void logWorkflowEvent(WorkflowEventInfo eventInfo) {
        LOGGER.debug("On - " + eventInfo.eventType() + ": workflowApplicationId: " + eventInfo.wfApplicationId()
                + ", workflowNamespace: " + eventInfo.wfNamespace() + ", workflowName: " + eventInfo.wfName()
                + ", workflowInstanceId: " + eventInfo.wfInstanceId() + ", workflowVersion: " + eventInfo.wfVersion());
    }

    private static void logTaskEvent(TaskEventInfo eventInfo) {
        LOGGER.debug("On - " + eventInfo.eventType() + ", taskName: " + eventInfo.taskName()
                + ", taskType: " + eventInfo.taskType() + ", taskId: " + eventInfo.taskId()
                + ", iteration: " + eventInfo.taskInstanceIteration()
                + ", isRetrying: " + eventInfo.taskInstanceRetrying()
                + ", retryAttempt: " + eventInfo.taskInstanceRetryAttempt()
                + ", retryCount: " + eventInfo.taskInstanceRetryCount());
    }

    // TODO remove this method the respective invocations.
    public static void printThreadAndCurrentVertxContext(String prefix) {
        Thread currentThread = Thread.currentThread();
        Boolean isVertxThread = null;
        Boolean isVertxWorker = null;

        if (currentThread instanceof VertxThread) {
            isVertxThread = true;
            isVertxWorker = ((VertxThread) currentThread).isWorker();
        }

        io.vertx.core.Context context = Vertx.currentContext();
        String vertxContextStr = null;
        Boolean isDuplicatedContext = null;

        if (context != null) {
            isDuplicatedContext = ((ContextInternal) context).isDuplicate();
            vertxContextStr = context.getClass().getName() + "@" + context.hashCode();
        }

        LOGGER.debug(prefix + " - Current thread: " + currentThread.getName() + ", isVertxThread: " + isVertxThread
                + ", isVertxWorker: " + isVertxWorker + " - Vertx context: " + vertxContextStr
                + ", isDuplicatedContext: " + isDuplicatedContext);
    }
}
