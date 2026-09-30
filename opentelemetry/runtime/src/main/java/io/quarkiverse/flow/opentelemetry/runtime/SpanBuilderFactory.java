package io.quarkiverse.flow.opentelemetry.runtime;

import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_ID_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_ITERATION_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_NAME_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_RETRYING_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_RETRY_ATTEMPT_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_TASK_TYPE_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_APPLICATION_ID_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_EXECUTION_IS_RESUMPTION_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_INSTANCE_ID_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_NAMESPACE_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_NAME_ATTR;
import static io.quarkiverse.flow.opentelemetry.runtime.SpanConstants.FLOW_WF_VERSION_ATTR;

import jakarta.inject.Inject;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class SpanBuilderFactory {

    @Inject
    Tracer tracer;

    public SpanBuilder newWorkflowCreateSpan(String workflowName, WorkflowEventInfo eventInfo, Context parentContext,
            SpanContext... spanContextLink) {
        return newWorkflowSpan(SpanUtils.generateWorkflowCreateSpanName(workflowName), eventInfo, parentContext,
                spanContextLink);
    }

    public SpanBuilder newWorkflowExecuteSpan(String workflowName, WorkflowEventInfo eventInfo, Context parentContext,
            boolean isResumption, SpanContext... spanContextLink) {
        return newWorkflowSpan(SpanUtils.generateWorkflowExecuteSpanName(workflowName), eventInfo, parentContext,
                spanContextLink)
                .setAttribute(FLOW_WF_EXECUTION_IS_RESUMPTION_ATTR, isResumption);
    }

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
                .setAttribute(FLOW_TASK_RETRY_ATTEMPT_ATTR, eventInfo.taskInstanceRetryAttempt());
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
