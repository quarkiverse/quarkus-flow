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
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowInstrumentationContext.getWorkflowInstrumentationContext;
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowInstrumentationContext.setWorkflowInstrumentationContext;

import java.time.Instant;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.quarkiverse.flow.opentelemetry.runtime.config.FlowOTelConfig;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.WorkflowPosition;
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

public class OTelWorkflowExecutionListener implements WorkflowExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OTelWorkflowExecutionListener.class);

    @ConfigProperty(name = "quarkus.flow.otel.task.name-strategy", defaultValue = "action-and-task-name")
    SpanUtils.TaskNameStrategy taskNameStrategy;

    @Inject
    SpanBuilderFactory spanBuilderFactory;

    @Inject
    FlowOTelConfig oTelConfig;

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
        if (!oTelConfig.isEnabled()) {
            return;
        }
        WorkflowEventInfo eventInfo = WorkflowEventInfo.from(ev);
        logWorkflowEvent(eventInfo);

        Context parentContext = Context.current();
        String workflowSpanName = generateWorkflowSpanName(eventInfo.wfName());

        Span startSpan = spanBuilderFactory.newWorkflowSpan(workflowSpanName, eventInfo,
                parentContext).startSpan();
        appendWorkflowEvent(startSpan, eventInfo.eventType());

        InstrumentationContext workflowInstanceContext = InstrumentationContext.newBuilder()
                .parentContext(parentContext)
                .withStartSpan(startSpan)
                .withStartTime(Instant.now())
                .build();

        setWorkflowInstrumentationContext(ev.workflowContext().instanceData(),
                new WorkflowInstrumentationContext(workflowInstanceContext));
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

        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(ev.workflowContext().instanceData());
        if (workflowContext == null) {
            warnNoWorkflowContext(eventInfo);
            return;
        }

        Span startSpan = workflowContext.getWorkflowInstanceContext().getStartSpan();
        if (eventInfo.eventType() == WORKFLOW_SUSPENDED || eventInfo.eventType() == WORKFLOW_RESUMED) {
            appendWorkflowEvent(startSpan, eventInfo.eventType());
        } else if (eventInfo.eventType() == WORKFLOW_COMPLETED || eventInfo.eventType() == WORKFLOW_CANCELLED) {
            startSpan.setStatus(StatusCode.OK);
            workflowContext.ensureAllTaskSpansAreClosed();
            appendWorkflowEvent(startSpan, eventInfo.eventType());
            startSpan.end();
        } else {
            WorkflowFailedEvent failedEvent = (WorkflowFailedEvent) ev;
            startSpan.recordException(failedEvent.cause());
            startSpan.setStatus(StatusCode.ERROR, failedEvent.cause().getMessage());
            workflowContext.ensureAllTaskSpansAreClosed();
            appendWorkflowEvent(startSpan, eventInfo.eventType());
            startSpan.end();
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

        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(ev.workflowContext().instanceData());
        if (workflowContext == null) {
            warnNoWorkflowContext(eventInfo);
            return;
        }
        InstrumentationContext parentTaskContext = workflowContext.findEnclosingParentContext(eventInfo.taskId());
        Context parentContext = parentTaskContext.getStartSpan().storeInContext(parentTaskContext.getParentContext());
        String spanName = generateTaskSpanName(taskNameStrategy, eventInfo.taskId(), eventInfo.taskName(),
                eventInfo.taskInstanceIteration(), eventInfo.taskInstanceRetryAttempt());

        SpanBuilder builder = spanBuilderFactory.newTaskSpan(spanName, eventInfo, parentContext);
        enrichSpan(builder, ev.taskContext().task());
        Span startSpan = builder.startSpan();

        appendTaskEvent(startSpan, eventInfo.eventType());

        InstrumentationContext taskInstanceContext = InstrumentationContext.newBuilder()
                .withJsonPosition(eventInfo.taskId())
                .withTaskType(eventInfo.taskType())
                .withContainerPosition(containerContextPosition(eventInfo.taskType(), ev.taskContext().position()))
                .withStartSpan(startSpan)
                .withStartTime(Instant.now())
                .parentContext(parentContext)
                .withIteration(eventInfo.taskInstanceIteration())
                .withRetrying(eventInfo.taskInstanceRetrying())
                .withRetryAttempt(eventInfo.taskInstanceRetryAttempt())
                .build();

        workflowContext.putTaskInstanceInstanceContext(eventInfo.taskId(),
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

        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(ev.workflowContext().instanceData());
        if (workflowContext == null) {
            warnNoWorkflowContext(eventInfo);
            return;
        }
        InstrumentationContext taskInstanceContext = workflowContext.getTaskInstanceContext(eventInfo.taskId(),
                eventInfo.taskInstanceIteration(), eventInfo.taskInstanceRetryAttempt());
        if (taskInstanceContext == null) {
            LOGGER.warn(
                    "No taskInstanceContext was found for taskType: {}, taskName: {}, taskId: {}, iteration: {}, isRetrying: {}, retryAttempt: {}",
                    eventInfo.taskType(), eventInfo.taskName(), eventInfo.taskId(), eventInfo.taskInstanceIteration(),
                    eventInfo.taskInstanceRetrying(), eventInfo.taskInstanceRetryAttempt());
            return;
        }

        if (TASK_CANCELLED == eventInfo.eventType() || TASK_COMPLETED == eventInfo.eventType()) {
            Span startSpan = taskInstanceContext.getStartSpan();
            appendTaskEvent(startSpan, eventInfo.eventType());
            startSpan.setStatus(StatusCode.OK);
            startSpan.end();
            workflowContext.removeTaskInstanceInstanceContext(eventInfo.taskId(),
                    eventInfo.taskInstanceIteration(),
                    eventInfo.taskInstanceRetryAttempt());
        } else if (TASK_SUSPENDED == eventInfo.eventType() || TASK_RESUMED == eventInfo.eventType()) {
            Span startSpan = taskInstanceContext.getStartSpan();
            appendTaskEvent(startSpan, eventInfo.eventType());
        } else {
            Span startSpan = taskInstanceContext.getStartSpan();
            TaskFailedEvent failedEvent = (TaskFailedEvent) ev;
            appendTaskEvent(startSpan, eventInfo.eventType());
            startSpan.recordException(failedEvent.cause());
            startSpan.setStatus(StatusCode.ERROR, failedEvent.cause().getMessage());
            startSpan.end();
            workflowContext.removeTaskInstanceInstanceContext(eventInfo.taskId(),
                    eventInfo.taskInstanceIteration(),
                    eventInfo.taskInstanceRetryAttempt());
        }
    }

    @Override
    public void close() {
        WorkflowExecutionListener.super.close();
    }

    private static String containerContextPosition(TaskType taskType, WorkflowPosition position) {
        switch (taskType) {
            case DO:
            case FOR:
            case LISTEN:
            case TRY:
            case FORK:
                int lastSize = position.last().toString().length();
                String jsonPointer = position.jsonPointer();
                return jsonPointer.substring(0, jsonPointer.length() - lastSize - 1);
            default:
                return null;
        }
    }

    private static void logWorkflowEvent(WorkflowEventInfo eventInfo) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "On - {}:, workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}",
                    eventInfo.eventType(), eventInfo.wfApplicationId(), eventInfo.wfNamespace(), eventInfo.wfName(),
                    eventInfo.wfInstanceId(), eventInfo.wfVersion());
        }
    }

    private static void logTaskEvent(TaskEventInfo eventInfo) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "On - {}: taskName: {}, taskType: {}, taskId: {}, iteration: {}, isRetrying: {}, retryAttempt: {}, retryCount: {}",
                    eventInfo.eventType(), eventInfo.taskName(), eventInfo.taskType(), eventInfo.taskId(),
                    eventInfo.taskInstanceIteration(), eventInfo.taskInstanceRetrying(), eventInfo.taskInstanceRetryAttempt(),
                    eventInfo.taskInstanceRetryCount());
        }
    }

    private static void warnNoWorkflowContext(WorkflowEventInfo eventInfo) {
        LOGGER.warn(
                "No instrumentation context was found for workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}",
                eventInfo.wfApplicationId(), eventInfo.wfNamespace(), eventInfo.wfName(),
                eventInfo.wfInstanceId(), eventInfo.wfVersion());
    }

    private static void warnNoWorkflowContext(TaskEventInfo eventInfo) {
        LOGGER.warn(
                "No instrumentation context was found for workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {} and, taskType: {}, taskName: {}, taskId: {}, iteration: {}, isRetrying: {}, retryAttempt: {}",
                eventInfo.wfApplicationId(), eventInfo.wfNamespace(), eventInfo.wfName(),
                eventInfo.wfInstanceId(), eventInfo.wfVersion(), eventInfo.taskType(), eventInfo.taskName(), eventInfo.taskId(),
                eventInfo.taskInstanceIteration(), eventInfo.taskInstanceRetrying(), eventInfo.taskInstanceRetryCount());
    }

    private void enrichSpan(SpanBuilder span, TaskBase task) {
        SpanUtils.getTaskSpanEnricher(task).enrich(span, task);
    }
}
