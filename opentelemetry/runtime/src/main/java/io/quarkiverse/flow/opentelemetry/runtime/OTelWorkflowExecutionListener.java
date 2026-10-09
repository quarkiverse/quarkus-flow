package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_CANCELLED;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_COMPLETED;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_FAULTED;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_JVM_SHUTDOWN;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.END_REASON_WORKFLOW_FAULTED;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_EXECUTION_END_REASON_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_EXECUTION_END_REASON_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.appendTaskEvent;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.appendWorkflowEvent;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanUtils.generateTaskSpanName;
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowInstrumentationContext.getWorkflowInstrumentationContext;
import static io.quarkiverse.flow.opentelemetry.runtime.WorkflowInstrumentationContext.setWorkflowInstrumentationContext;
import static io.serverlessworkflow.impl.lifecycle.EventType.TASK_CANCELLED;
import static io.serverlessworkflow.impl.lifecycle.EventType.TASK_COMPLETED;
import static io.serverlessworkflow.impl.lifecycle.EventType.TASK_RESUMED;
import static io.serverlessworkflow.impl.lifecycle.EventType.TASK_SUSPENDED;
import static io.serverlessworkflow.impl.lifecycle.EventType.WORKFLOW_CANCELLED;
import static io.serverlessworkflow.impl.lifecycle.EventType.WORKFLOW_COMPLETED;
import static io.serverlessworkflow.impl.lifecycle.EventType.WORKFLOW_RESUMED;
import static io.serverlessworkflow.impl.lifecycle.EventType.WORKFLOW_SUSPENDED;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.quarkiverse.flow.opentelemetry.runtime.config.FlowOTelConfig;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowMutableInstance;
import io.serverlessworkflow.impl.WorkflowPosition;
import io.serverlessworkflow.impl.lifecycle.EventType;
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

    private static final String OTEL_CREATE_TRACE_ID = "OTEL_CREATE_TRACE_ID";
    private static final String OTEL_CREATE_SPAN_ID = "OTEL_CREATE_SPAN_ID";

    /**
     * Experimental Quarkus Flow development only property, please don't use.
     */
    @ConfigProperty(name = "quarkus.flow.otel.task.name-strategy", defaultValue = "action-and-task-name")
    SpanUtils.TaskNameStrategy taskNameStrategy;

    @Inject
    SpanBuilderFactory spanBuilderFactory;

    @Inject
    FlowOTelConfig oTelConfig;

    private final ConcurrentHashMap<String, WorkflowInstrumentationContext> activeWorkflowContextRegistry = new ConcurrentHashMap<>();

    @Override
    public void onWorkflowStarted(WorkflowStartedEvent ev) {
        if (!oTelConfig.isEnabled()) {
            return;
        }
        WorkflowEventInfo eventInfo = WorkflowEventInfo.from(ev);
        logWorkflowEvent(eventInfo);

        Context parentContext = Context.current();
        Span createSpan = spanBuilderFactory.newWorkflowCreateSpan(eventInfo.wfName(), eventInfo, parentContext).startSpan();

        String createTraceId = createSpan.getSpanContext().getTraceId();
        String createSpanId = createSpan.getSpanContext().getSpanId();
        ((WorkflowMutableInstance) ev.workflowContext().instanceData()).addMetadataIfAbsent(OTEL_CREATE_TRACE_ID,
                () -> createTraceId);
        ((WorkflowMutableInstance) ev.workflowContext().instanceData()).addMetadataIfAbsent(OTEL_CREATE_SPAN_ID,
                () -> createSpanId);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Workflow create span was started for workflowInstanceId: {} from createTraceId: {}, createSpanId: {}",
                    eventInfo.wfInstanceId(), createTraceId, createSpanId);
        }

        Span startSpan = spanBuilderFactory
                .newWorkflowExecuteSpan(eventInfo.wfName(), eventInfo, createSpan.storeInContext(parentContext), false)
                .startSpan();
        createSpan.end();
        appendWorkflowEvent(startSpan, eventInfo.eventType());

        InstrumentationContext workflowInstanceContext = InstrumentationContext.newBuilder()
                .parentContext(parentContext)
                .withStartSpan(startSpan)
                .withStartTime(Instant.now())
                .build();

        WorkflowInstrumentationContext workflowInstrumentationContext = new WorkflowInstrumentationContext(
                ev.workflowContext().instanceData(),
                workflowInstanceContext);
        setWorkflowInstrumentationContext(ev.workflowContext().instanceData(), workflowInstrumentationContext);
        activeWorkflowContextRegistry.put(eventInfo.wfInstanceId(), workflowInstrumentationContext);
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

        WorkflowInstrumentationContext workflowContext = getOrResumeWorkflowContext(ev.workflowContext(), eventInfo);
        if (workflowContext == null) {
            warnNoWorkflowContext(eventInfo);
            return;
        }

        if (eventInfo.eventType() == WORKFLOW_SUSPENDED || eventInfo.eventType() == WORKFLOW_RESUMED) {
            Span startSpan = workflowContext.getWorkflowInstanceContext().getStartSpan();
            appendWorkflowEvent(startSpan, eventInfo.eventType());
        } else if (eventInfo.eventType() == WORKFLOW_COMPLETED || eventInfo.eventType() == WORKFLOW_CANCELLED) {
            activeWorkflowContextRegistry.remove(eventInfo.wfInstanceId());
            workflowContext.getWorkflowInstanceContext().endStartSpan(startSpan -> {
                startSpan.setStatus(StatusCode.OK);
                String endReason = eventInfo.eventType() == WORKFLOW_COMPLETED ? END_REASON_COMPLETED : END_REASON_CANCELLED;
                startSpan.setAttribute(FLOW_WF_EXECUTION_END_REASON_ATTR, endReason);
                workflowContext.ensureAllTaskSpansAreClosed(endReason);
                appendWorkflowEvent(startSpan, eventInfo.eventType());
            });
        } else {
            activeWorkflowContextRegistry.remove(eventInfo.wfInstanceId());
            workflowContext.getWorkflowInstanceContext().endStartSpan(startSpan -> {
                WorkflowFailedEvent failedEvent = (WorkflowFailedEvent) ev;
                startSpan.recordException(failedEvent.cause());
                startSpan.setStatus(StatusCode.ERROR, failedEvent.cause().getMessage());
                startSpan.setAttribute(ERROR_TYPE, failedEvent.cause().getClass().getName());
                startSpan.setAttribute(FLOW_WF_EXECUTION_END_REASON_ATTR, END_REASON_FAULTED);
                workflowContext.ensureAllTaskSpansAreClosed(END_REASON_WORKFLOW_FAULTED);
                appendWorkflowEvent(startSpan, eventInfo.eventType());
            });
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

        WorkflowInstrumentationContext workflowContext = getOrResumeWorkflowContext(ev.workflowContext(), eventInfo);
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

        appendTaskEvent(startSpan, ev.type());

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

    private WorkflowInstrumentationContext getOrResumeWorkflowContext(WorkflowContextData workflowContextData,
            TaskEventInfo eventInfo) {
        WorkflowEventInfo wfWorkflowEventInfo = new WorkflowEventInfo(eventInfo.wfApplicationId(),
                eventInfo.wfNamespace(), eventInfo.wfName(), eventInfo.wfVersion(), eventInfo.wfInstanceId(),
                EventType.WORKFLOW_STARTED);
        return getOrResumeWorkflowContext(workflowContextData, wfWorkflowEventInfo);
    }

    private WorkflowInstrumentationContext getOrResumeWorkflowContext(WorkflowContextData workflowContextData,
            WorkflowEventInfo eventInfo) {
        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(workflowContextData.instanceData());
        if (workflowContext == null) {
            String createTraceId = workflowContextData.instanceData().findMetadata(OTEL_CREATE_TRACE_ID, String.class)
                    .orElse(null);
            String createSpanId = workflowContextData.instanceData().findMetadata(OTEL_CREATE_SPAN_ID, String.class)
                    .orElse(null);
            if (createTraceId == null || createSpanId == null) {
                return null;
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Creating workflow context for durable workflow from existing createTraceId: {}, createSpanId: {}",
                            createTraceId,
                            createSpanId);
                }
                Context parentContext = Context.current();
                SpanContext createSpanContext = SpanContext.createFromRemoteParent(
                        createTraceId,
                        createSpanId,
                        TraceFlags.getSampled(),
                        TraceState.getDefault());

                Span startSpan = spanBuilderFactory.newWorkflowExecuteSpan(eventInfo.wfName(), eventInfo,
                        parentContext, true, createSpanContext).startSpan();
                appendWorkflowEvent(startSpan, EventType.WORKFLOW_STARTED);

                InstrumentationContext workflowInstanceContext = InstrumentationContext.newBuilder()
                        .parentContext(parentContext)
                        .withStartSpan(startSpan)
                        .withStartTime(Instant.now())
                        .build();

                workflowContext = new WorkflowInstrumentationContext(workflowContextData.instanceData(),
                        workflowInstanceContext);
                setWorkflowInstrumentationContext(workflowContextData.instanceData(), workflowContext);
                activeWorkflowContextRegistry.put(eventInfo.wfInstanceId(), workflowContext);
            }
        }
        return workflowContext;
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
            workflowContext.removeTaskInstanceInstanceContext(eventInfo.taskId(),
                    eventInfo.taskInstanceIteration(),
                    eventInfo.taskInstanceRetryAttempt());
            taskInstanceContext.endStartSpan(startSpan -> {
                appendTaskEvent(startSpan, eventInfo.eventType());
                startSpan.setStatus(StatusCode.OK);
                String endReason = TASK_CANCELLED == eventInfo.eventType() ? END_REASON_CANCELLED : END_REASON_COMPLETED;
                startSpan.setAttribute(FLOW_TASK_EXECUTION_END_REASON_ATTR, endReason);
            });
        } else if (TASK_SUSPENDED == eventInfo.eventType() || TASK_RESUMED == eventInfo.eventType()) {
            Span startSpan = taskInstanceContext.getStartSpan();
            appendTaskEvent(startSpan, eventInfo.eventType());
        } else {
            workflowContext.removeTaskInstanceInstanceContext(eventInfo.taskId(),
                    eventInfo.taskInstanceIteration(),
                    eventInfo.taskInstanceRetryAttempt());
            taskInstanceContext.endStartSpan(startSpan -> {
                TaskFailedEvent failedEvent = (TaskFailedEvent) ev;
                appendTaskEvent(startSpan, eventInfo.eventType());
                startSpan.recordException(failedEvent.cause());
                startSpan.setStatus(StatusCode.ERROR, failedEvent.cause().getMessage());
                startSpan.setAttribute(ERROR_TYPE, failedEvent.cause().getClass().getName());
                startSpan.setAttribute(FLOW_TASK_EXECUTION_END_REASON_ATTR, END_REASON_FAULTED);
            });
        }
    }

    @Override
    public void close() {
        activeWorkflowContextRegistry.forEach((workflowInstanceId, workflowInstrumentationContext) -> {
            LOGGER.warn("Ending instrumentation context for active workflow run due to JVM shutdown, workflowInstanceId: {}",
                    workflowInstanceId);
            workflowInstrumentationContext.failActiveTaskSpans("Task execution interrupted due to JVM shutdown",
                    SpanConstants.ERROR_TYPE_RUNTIME_JVM_SHUTDOWN_ATTR, END_REASON_JVM_SHUTDOWN);

            workflowInstrumentationContext.getWorkflowInstanceContext().endStartSpan(startSpan -> {
                startSpan.setStatus(StatusCode.ERROR, "Workflow execution interrupted due to JVM shutdown");
                startSpan.setAttribute(ERROR_TYPE, SpanConstants.ERROR_TYPE_RUNTIME_JVM_SHUTDOWN_ATTR);
                startSpan.setAttribute(FLOW_WF_EXECUTION_END_REASON_ATTR, END_REASON_JVM_SHUTDOWN);
            });
        });
        activeWorkflowContextRegistry.clear();
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
                    "On - {}: workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}",
                    eventInfo.eventType(), eventInfo.wfApplicationId(), eventInfo.wfNamespace(), eventInfo.wfName(),
                    eventInfo.wfInstanceId(), eventInfo.wfVersion());
        }
    }

    private static void logTaskEvent(TaskEventInfo eventInfo) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "On - {}: workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}"
                            +
                            ", taskName: {}, taskType: {}, taskId: {}, iteration: {}, isRetrying: {}, retryAttempt: {}, retryCount: {}",
                    eventInfo.eventType(), eventInfo.wfApplicationId(), eventInfo.wfNamespace(), eventInfo.wfName(),
                    eventInfo.wfInstanceId(), eventInfo.wfVersion(),
                    eventInfo.taskName(), eventInfo.taskType(), eventInfo.taskId(),
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
