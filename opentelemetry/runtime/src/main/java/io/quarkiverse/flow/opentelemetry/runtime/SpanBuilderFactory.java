package io.quarkiverse.flow.opentelemetry.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

@ApplicationScoped
public class SpanBuilderFactory {

    static final String FLOW_WF_APPLICATION_ID_ATTR = "flow.application.id";
    static final String FLOW_WF_ID_ATTR = "flow.workflow.id";
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

    public SpanBuilder newWorkflowSpan(String name, String workflowApplicationId, String workflowNamespace, String workflowName,
            String workflowInstanceId, String workflowVersion, Context parentContext, SpanContext... spanContextLink) {
        SpanBuilder builder = tracer.spanBuilder(name);
        applyWorkflowAttributes(builder, workflowApplicationId, workflowNamespace, workflowName, workflowInstanceId,
                workflowVersion);
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
                .setAttribute(FLOW_WF_ID_ATTR, workflowInstanceId)
                .setAttribute(FLOW_WF_VERSION_ATTR, workflowVersion);
    }

    public SpanBuilder newTaskSpan(String name, String workflowApplicationId, String workflowNamespace, String workflowName,
            String workflowInstanceId, String workflowVersion,
            String taskInstanceId, String taskType, String taskName,
            int iteration, boolean retrying, int retryAttempt,
            Context parentContext, SpanContext... spanContextLink) {
        SpanBuilder builder = tracer.spanBuilder(name);
        applyWorkflowAttributes(builder, workflowApplicationId, workflowNamespace, workflowName, workflowInstanceId,
                workflowVersion);
        builder.setAttribute(FLOW_TASK_ID_ATTR, taskInstanceId)
                .setAttribute(FLOW_TASK_TYPE_ATTR, taskType)
                .setAttribute(FLOW_TASK_NAME_ATTR, taskName)
                .setAttribute(FLOW_TASK_ITERATION_ATTR, iteration)
                .setAttribute(FLOW_TASK_RETRYING_ATTR, retrying)
                .setAttribute(FLOW_TASK_RETRY_ATTEMPT, retryAttempt);
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
