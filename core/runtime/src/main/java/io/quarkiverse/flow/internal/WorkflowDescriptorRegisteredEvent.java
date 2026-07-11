package io.quarkiverse.flow.internal;

import io.serverlessworkflow.api.types.Workflow;

/**
 * Fired when a new {@link io.serverlessworkflow.api.types.Workflow} is registered.
 */
public record WorkflowDescriptorRegisteredEvent(Workflow workflow) {
}
