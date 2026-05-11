package org.acme.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single task in the build pipeline.
 */
public record BuildTask(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("projectName") String projectName,
        @JsonProperty("gitRef") String gitRef) {
}
