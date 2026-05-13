package org.acme.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Input specification for a build pipeline.
 * Defines the project to build and the tasks to execute.
 */
public record BuildSpec(
        @JsonProperty("projectName") String projectName,
        @JsonProperty("gitRef") String gitRef,
        @JsonProperty("tasks") List<String> tasks) {
    public static BuildSpec createDefault(String projectName) {
        return new BuildSpec(
                projectName,
                "main",
                List.of("lint", "test", "build", "deploy"));
    }
}
