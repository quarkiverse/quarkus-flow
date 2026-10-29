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
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.WorkflowMutableInstance;
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
        String workflowInstanceId = ev.workflowContext().instanceData().id();
        logWorkflowEvent(ev);

        Context parentContext = Context.current();
        Span createSpan = spanBuilderFactory.newWorkflowCreateSpan(ev.workflowContext().definition(),
                workflowInstanceId, parentContext).startSpan();

        String createTraceId = createSpan.getSpanContext().getTraceId();
        String createSpanId = createSpan.getSpanContext().getSpanId();
        ((WorkflowMutableInstance) ev.workflowContext().instanceData()).addMetadataIfAbsent(OTEL_CREATE_TRACE_ID,
                () -> createTraceId);
        ((WorkflowMutableInstance) ev.workflowContext().instanceData()).addMetadataIfAbsent(OTEL_CREATE_SPAN_ID,
                () -> createSpanId);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Workflow create span was started for workflowInstanceId: {} from createTraceId: {}, createSpanId: {}",
                    workflowInstanceId, createTraceId, createSpanId);
        }

        Span startSpan = spanBuilderFactory
                .newWorkflowExecuteSpan(ev.workflowContext().definition(), workflowInstanceId,
                        createSpan.storeInContext(parentContext), false)
                .startSpan();
        createSpan.end();
        appendWorkflowEvent(startSpan, ev.type());

        InstrumentationContext workflowInstanceContext = InstrumentationContext.newBuilder()
                .parentContext(parentContext)
                .withStartSpan(startSpan)
                .withStartTime(Instant.now())
                .build();

        WorkflowInstrumentationContext workflowInstrumentationContext = new WorkflowInstrumentationContext(
                ev.workflowContext().instanceData(), workflowInstanceContext);
        setWorkflowInstrumentationContext(ev.workflowContext().instanceData(), workflowInstrumentationContext);
        activeWorkflowContextRegistry.put(workflowInstanceId, workflowInstrumentationContext);
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
        String workflowInstanceId = ev.workflowContext().instanceData().id();
        logWorkflowEvent(ev);

        WorkflowInstrumentationContext workflowContext = getOrResumeWorkflowContext(ev.workflowContext());
        if ((workflowContext == null) && (ev.type() == WORKFLOW_CANCELLED)) {
            // The engine can clear the instance metadata before publishing the cancelled event (e.g. when cancelling
            // an instance waiting on a listen task). WorkflowInstrumentationContext.close() has already ended the
            // workflow span in that case.
            activeWorkflowContextRegistry.remove(workflowInstanceId);
            LOGGER.debug("Workflow span already ended on close for cancelled workflowName: {}, workflowInstanceId: {}",
                    ev.workflowContext().definition().id().name(), workflowInstanceId);
        } else if (workflowContext == null) {
            warnNoWorkflowContext(ev);
        } else if ((ev.type() == WORKFLOW_SUSPENDED) || (ev.type() == WORKFLOW_RESUMED)) {
            Span startSpan = workflowContext.getWorkflowInstanceContext().getStartSpan();
            appendWorkflowEvent(startSpan, ev.type());
        } else if ((ev.type() == WORKFLOW_COMPLETED) || (ev.type() == WORKFLOW_CANCELLED)) {
            activeWorkflowContextRegistry.remove(workflowInstanceId);
            workflowContext.getWorkflowInstanceContext().endStartSpan(startSpan -> {
                startSpan.setStatus(StatusCode.OK);
                String endReason = ev.type() == WORKFLOW_COMPLETED ? END_REASON_COMPLETED : END_REASON_CANCELLED;
                startSpan.setAttribute(FLOW_WF_EXECUTION_END_REASON_ATTR, endReason);
                workflowContext.ensureAllTaskSpansAreClosed(endReason);
                appendWorkflowEvent(startSpan, ev.type());
            });
        } else {
            activeWorkflowContextRegistry.remove(workflowInstanceId);
            workflowContext.getWorkflowInstanceContext().endStartSpan(startSpan -> {
                WorkflowFailedEvent failedEvent = (WorkflowFailedEvent) ev;
                startSpan.recordException(failedEvent.cause());
                startSpan.setStatus(StatusCode.ERROR, failedEvent.cause().getMessage());
                startSpan.setAttribute(ERROR_TYPE, failedEvent.cause().getClass().getName());
                startSpan.setAttribute(FLOW_WF_EXECUTION_END_REASON_ATTR, END_REASON_FAULTED);
                workflowContext.ensureAllTaskSpansAreClosed(END_REASON_WORKFLOW_FAULTED);
                appendWorkflowEvent(startSpan, ev.type());
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
        String workflowInstanceId = ev.workflowContext().instanceData().id();
        TaskContext taskContext = (TaskContext) ev.taskContext();
        String taskId = taskContext.position().jsonPointer();
        logTaskEvent(ev);

        WorkflowInstrumentationContext workflowContext = getOrResumeWorkflowContext(ev.workflowContext());
        if (workflowContext == null) {
            warnNoWorkflowContext(ev);
            return;
        }
        InstrumentationContext parentTaskContext = workflowContext.findEnclosingParentContext(taskId);
        Context parentContext = parentTaskContext.getStartSpan().storeInContext(parentTaskContext.getParentContext());
        String spanName = generateTaskSpanName(taskContext.taskName());
        SpanBuilder builder = spanBuilderFactory.newTaskSpan(spanName, ev.workflowContext().definition(), workflowInstanceId,
                taskContext, parentContext);
        enrichSpan(builder, ev.taskContext().task());
        Span startSpan = builder.startSpan();

        appendTaskEvent(startSpan, ev.type());

        InstrumentationContext taskInstanceContext = InstrumentationContext.newBuilder()
                .withJsonPosition(taskId)
                .withTaskType(TaskType.fromTask(taskContext.task()))
                .withContainerPosition(containerContextPosition(taskContext))
                .withStartSpan(startSpan)
                .withStartTime(Instant.now())
                .parentContext(parentContext)
                .withIteration(taskContext.iteration())
                .withRetrying(taskContext.isRetrying())
                .withRetryAttempt(taskContext.retryAttempt())
                .build();

        workflowContext.putTaskInstanceInstanceContext(taskId, taskContext.iteration(), taskContext.retryAttempt(),
                taskInstanceContext);
    }

    private WorkflowInstrumentationContext getOrResumeWorkflowContext(WorkflowContextData workflowContextData) {
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

                Span startSpan = spanBuilderFactory
                        .newWorkflowExecuteSpan(workflowContextData.definition(), workflowContextData.instanceData().id(),
                                parentContext, true, createSpanContext)
                        .startSpan();

                InstrumentationContext workflowInstanceContext = InstrumentationContext.newBuilder()
                        .parentContext(parentContext)
                        .withStartSpan(startSpan)
                        .withStartTime(Instant.now())
                        .build();

                workflowContext = new WorkflowInstrumentationContext(workflowContextData.instanceData(),
                        workflowInstanceContext);
                setWorkflowInstrumentationContext(workflowContextData.instanceData(), workflowContext);
                activeWorkflowContextRegistry.put(workflowContextData.instanceData().id(), workflowContext);
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
        TaskContext taskContext = (TaskContext) ev.taskContext();
        String taskId = taskContext.position().jsonPointer();
        logTaskEvent(ev);

        WorkflowInstrumentationContext workflowContext = getWorkflowInstrumentationContext(ev.workflowContext().instanceData());
        if (workflowContext == null) {
            warnNoWorkflowContext(ev);
            return;
        }
        InstrumentationContext taskInstanceContext = workflowContext.getTaskInstanceContext(taskId,
                taskContext.iteration(), taskContext.retryAttempt());
        if (taskInstanceContext == null) {
            LOGGER.warn(
                    "No taskInstanceContext was found for taskType: {}, taskName: {}, taskId: {}, iteration: {}, isRetrying: {}, retryAttempt: {}",
                    TaskType.fromTask(taskContext.task()), taskContext.taskName(), taskId, taskContext.iteration(),
                    taskContext.isRetrying(), taskContext.retryAttempt());
            return;
        }

        if (TASK_CANCELLED == ev.type() || TASK_COMPLETED == ev.type()) {
            workflowContext.removeTaskInstanceInstanceContext(taskId, taskContext.iteration(), taskContext.retryAttempt());
            taskInstanceContext.endStartSpan(startSpan -> {
                appendTaskEvent(startSpan, ev.type());
                startSpan.setStatus(StatusCode.OK);
                String endReason = TASK_CANCELLED == ev.type() ? END_REASON_CANCELLED : END_REASON_COMPLETED;
                startSpan.setAttribute(FLOW_TASK_EXECUTION_END_REASON_ATTR, endReason);
            });
        } else if (TASK_SUSPENDED == ev.type() || TASK_RESUMED == ev.type()) {
            Span startSpan = taskInstanceContext.getStartSpan();
            appendTaskEvent(startSpan, ev.type());
        } else {
            workflowContext.removeTaskInstanceInstanceContext(taskId, taskContext.iteration(), taskContext.retryAttempt());
            taskInstanceContext.endStartSpan(startSpan -> {
                TaskFailedEvent failedEvent = (TaskFailedEvent) ev;
                appendTaskEvent(startSpan, ev.type());
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

    private static String containerContextPosition(TaskContext taskContext) {
        switch (TaskType.fromTask(taskContext.task())) {
            case DO:
            case FOR:
            case LISTEN:
            case TRY:
            case FORK:
                int lastSize = taskContext.position().last().toString().length();
                String jsonPointer = taskContext.position().jsonPointer();
                return jsonPointer.substring(0, jsonPointer.length() - lastSize - 1);
            default:
                return null;
        }
    }

    private static void logWorkflowEvent(WorkflowEvent ev) {
        if (LOGGER.isDebugEnabled()) {
            WorkflowDefinitionData definitionData = ev.workflowContext().definition();
            LOGGER.debug(
                    "On - {}: workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}",
                    ev.type(), definitionData.application().id(), definitionData.id().namespace(), definitionData.id().name(),
                    ev.workflowContext().instanceData().id(), definitionData.id().version());
        }
    }

    private static void logTaskEvent(TaskEvent ev) {
        if (LOGGER.isDebugEnabled()) {
            WorkflowDefinitionData definitionData = ev.workflowContext().definition();
            TaskContext taskContext = (TaskContext) ev.taskContext();
            LOGGER.debug(
                    "On - {}: workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}"
                            +
                            ", taskName: {}, taskType: {}, taskId: {}, iteration: {}, isRetrying: {}, retryAttempt: {}, retryCount: {}",
                    ev.type(), definitionData.application().id(), definitionData.id().namespace(), definitionData.id().name(),
                    ev.workflowContext().instanceData().id(), definitionData.id().version(),
                    taskContext.taskName(), TaskType.fromTask(taskContext.task()), taskContext.position().jsonPointer(),
                    taskContext.iteration(), taskContext.isRetrying(), taskContext.retryAttempt(),
                    taskContext.tryRetryCount().orElse(0));
        }
    }

    private static void warnNoWorkflowContext(WorkflowEvent ev) {
        WorkflowDefinitionData definitionData = ev.workflowContext().definition();
        LOGGER.warn(
                "No instrumentation context was found for workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}",
                definitionData.application().id(), definitionData.id().namespace(), definitionData.id().name(),
                ev.workflowContext().instanceData().id(), definitionData.id().version());
    }

    private static void warnNoWorkflowContext(TaskEvent ev) {
        WorkflowDefinitionData definitionData = ev.workflowContext().definition();
        TaskContext taskContext = (TaskContext) ev.taskContext();
        LOGGER.warn(
                "No instrumentation context was found for workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {} and, taskType: {}, taskName: {}, taskId: {}, iteration: {}, isRetrying: {}, retryAttempt: {}",
                definitionData.application().id(), definitionData.id().namespace(), definitionData.id().name(),
                ev.workflowContext().instanceData().id(), definitionData.id().version(), TaskType.fromTask(taskContext.task()),
                taskContext.taskName(), taskContext.position().jsonPointer(),
                taskContext.iteration(), taskContext.isRetrying(), taskContext.retryAttempt());
    }

    private void enrichSpan(SpanBuilder span, TaskBase task) {
        SpanUtils.getTaskSpanEnricher(task).enrich(span, task);
    }
}
