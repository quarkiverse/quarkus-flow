package io.quarkiverse.flow.internal;

/**
 * Fired when the {@link io.serverlessworkflow.impl.WorkflowApplication} is ready.
 *
 * @param id the {@link io.serverlessworkflow.impl.WorkflowApplication} id.
 */
public record WorkflowApplicationReadyEvent(String id) {
}
