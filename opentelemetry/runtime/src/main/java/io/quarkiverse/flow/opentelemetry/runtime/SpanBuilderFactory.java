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
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowDefinitionData;

public class SpanBuilderFactory {

    @Inject
    Tracer tracer;

    public SpanBuilder newWorkflowCreateSpan(WorkflowDefinitionData workflowDefinition, String workflowInstanceId,
            Context parentContext,
            SpanContext... spanContextLink) {
        return newWorkflowSpan(SpanUtils.generateWorkflowCreateSpanName(workflowDefinition.id().name()), workflowDefinition,
                workflowInstanceId, parentContext, spanContextLink);
    }

    public SpanBuilder newWorkflowExecuteSpan(WorkflowDefinitionData workflowDefinition, String workflowInstanceId,
            Context parentContext,
            boolean isResumption, SpanContext... spanContextLink) {
        return newWorkflowSpan(SpanUtils.generateWorkflowExecuteSpanName(workflowDefinition.id().name()), workflowDefinition,
                workflowInstanceId, parentContext,
                spanContextLink)
                .setAttribute(FLOW_WF_EXECUTION_IS_RESUMPTION_ATTR, isResumption);
    }

    public SpanBuilder newWorkflowSpan(String name, WorkflowDefinitionData workflowDefinition, String workflowInstanceId,
            Context parentContext,
            SpanContext... spanContextLink) {
        SpanBuilder builder = tracer.spanBuilder(name);
        applyWorkflowAttributes(builder, workflowDefinition.application().id(), workflowDefinition.id().namespace(),
                workflowDefinition.id().name(), workflowInstanceId, workflowDefinition.id().version());
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

    public SpanBuilder newTaskSpan(String name, WorkflowDefinitionData workflowDefinitionData, String workflowInstanceId,
            TaskContext taskContext, Context parentContext,
            SpanContext... spanContextLink) {
        SpanBuilder builder = tracer.spanBuilder(name);
        applyWorkflowAttributes(builder, workflowDefinitionData.application().id(), workflowDefinitionData.id().namespace(),
                workflowDefinitionData.id().name(), workflowInstanceId, workflowDefinitionData.id().version());
        builder.setAttribute(FLOW_TASK_ID_ATTR, taskContext.position().jsonPointer())
                .setAttribute(FLOW_TASK_TYPE_ATTR, TaskType.fromTask(taskContext.task()).toString())
                .setAttribute(FLOW_TASK_NAME_ATTR, taskContext.taskName())
                .setAttribute(FLOW_TASK_ITERATION_ATTR, taskContext.iteration())
                .setAttribute(FLOW_TASK_RETRYING_ATTR, taskContext.isRetrying())
                .setAttribute(FLOW_TASK_RETRY_ATTEMPT_ATTR, taskContext.retryAttempt());
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
