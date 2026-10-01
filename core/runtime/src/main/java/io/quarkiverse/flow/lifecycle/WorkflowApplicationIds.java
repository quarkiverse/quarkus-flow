package io.quarkiverse.flow.lifecycle;

import io.serverlessworkflow.impl.lifecycle.WorkflowEvent;

/**
 * Single source of the {@code workflowApplicationId} exposed on lifecycle events.
 * <p>
 * The workflow application ID identifies which {@link io.serverlessworkflow.impl.WorkflowApplication} (e.g. which
 * Runner pod / durable-kubernetes lease) processed a workflow instance. It is added to every workflow and task
 * lifecycle event, both on the CloudEvents data payload and on the structured logging events, so consumers such as
 * the Data Index can correlate instances with the application that executed them.
 */
public final class WorkflowApplicationIds {

    /**
     * Name of the field carrying the workflow application ID on lifecycle events.
     */
    public static final String FIELD_NAME = "workflowApplicationId";

    private WorkflowApplicationIds() {
    }

    /**
     * Resolves the ID of the {@link io.serverlessworkflow.impl.WorkflowApplication} that produced the given event.
     */
    public static String from(WorkflowEvent event) {
        return event.workflowContext().definition().application().id();
    }
}
