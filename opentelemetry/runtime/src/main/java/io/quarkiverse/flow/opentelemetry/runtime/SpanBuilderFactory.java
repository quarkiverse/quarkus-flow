package io.quarkiverse.flow.opentelemetry.runtime;

import jakarta.inject.Inject;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class SpanBuilderFactory {

    static final String FLOW_WF_APPLICATION_ID_ATTR = "flow.application.id";
    static final String FLOW_WF_INSTANCE_ID_ATTR = "flow.workflow.instance.id";
    static final String FLOW_WF_NAME_ATTR = "flow.workflow.name";
    static final String FLOW_WF_NAMESPACE_ATTR = "flow.workflow.namespace";
    static final String FLOW_WF_VERSION_ATTR = "flow.workflow.version";

    static final String FLOW_TASK_ID_ATTR = "flow.task.id";
    static final String FLOW_TASK_TYPE_ATTR = "flow.task.type";
    static final String FLOW_TASK_NAME_ATTR = "flow.task.name";
    static final String FLOW_TASK_ITERATION_ATTR = "flow.task.iteration";
    static final String FLOW_TASK_RETRYING_ATTR = "flow.task.retrying";
    static final String FLOW_TASK_RETRY_ATTEMPT = "flow.task.retry_attempt";

    @Inject
    Tracer tracer;

    public SpanBuilder newWorkflowSpan(String name, WorkflowEventInfo eventInfo, Context parentContext,
            SpanContext... spanContextLink) {
        SpanBuilder builder = tracer.spanBuilder(name);
        applyWorkflowAttributes(builder, eventInfo.wfApplicationId(), eventInfo.wfNamespace(), eventInfo.wfName(),
                eventInfo.wfInstanceId(), eventInfo.wfVersion());
        if (parentContext != null) {
            builder.setParent(parentContext);
        }
        if (spanContextLink != null) {
            for (SpanContext contextLink : spanContextLink)
                builder.addLink(contextLink);
        }
        return builder;
    }

    private static void applyWorkflowAttributes(SpanBuilder spanBuilder, String workflowApplicationId, String workflowNamespace,
            String workflowName,
            String workflowInstanceId, String workflowVersion) {
        spanBuilder.setAttribute(FLOW_WF_APPLICATION_ID_ATTR, workflowApplicationId)
                .setAttribute(FLOW_WF_NAMESPACE_ATTR, workflowNamespace)
                .setAttribute(FLOW_WF_NAME_ATTR, workflowName)
                .setAttribute(FLOW_WF_INSTANCE_ID_ATTR, workflowInstanceId)
                .setAttribute(FLOW_WF_VERSION_ATTR, workflowVersion);
    }

    public SpanBuilder newTaskSpan(String name, TaskEventInfo eventInfo, Context parentContext,
            SpanContext... spanContextLink) {
        SpanBuilder builder = tracer.spanBuilder(name);
        applyWorkflowAttributes(builder, eventInfo.wfApplicationId(), eventInfo.wfNamespace(), eventInfo.wfName(),
                eventInfo.wfInstanceId(),
                eventInfo.wfVersion());
        builder.setAttribute(FLOW_TASK_ID_ATTR, eventInfo.taskId())
                .setAttribute(FLOW_TASK_TYPE_ATTR, eventInfo.taskType().toString())
                .setAttribute(FLOW_TASK_NAME_ATTR, eventInfo.taskName())
                .setAttribute(FLOW_TASK_ITERATION_ATTR, eventInfo.taskInstanceIteration())
                .setAttribute(FLOW_TASK_RETRYING_ATTR, eventInfo.taskInstanceRetrying())
                .setAttribute(FLOW_TASK_RETRY_ATTEMPT, eventInfo.taskInstanceRetryAttempt());
        if (parentContext != null) {
            builder.setParent(parentContext);
        }
        if (spanContextLink != null) {
            for (SpanContext contextLink : spanContextLink)
                builder.addLink(contextLink);
        }
        return builder;
    }

}
