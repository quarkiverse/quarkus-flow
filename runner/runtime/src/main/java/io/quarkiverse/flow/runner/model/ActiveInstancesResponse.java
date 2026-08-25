package io.quarkiverse.flow.runner.model;

import java.util.List;

/**
 * Response payload for {@code GET /q/flow/instances}.
 * Returns the {@code applicationId} of this runner and the list of
 * in-memory active workflow instances currently tracked by the registry.
 */
public record ActiveInstancesResponse(String applicationId, List<InstanceSnapshot> instances) {
}
