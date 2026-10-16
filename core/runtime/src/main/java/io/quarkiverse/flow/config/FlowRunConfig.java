package io.quarkiverse.flow.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * Run task configuration for Quarkus Flow.
 * <p>
 * Controls the behavior of {@code run.shell} task execution.
 */
@ConfigGroup
public interface FlowRunConfig {

    /**
     * Shell execution settings.
     */
    Shell shell();

    /**
     * Shell execution configuration.
     */
    interface Shell {

        /**
         * List of executable names that workflows are allowed to invoke via {@code run.shell} tasks.
         * Each entry must be an exact match on the executable name (e.g. {@code curl}, {@code jq}).
         * <p>
         * An empty list (the default) disables all shell execution.
         */
        Optional<List<String>> allowedCommands();

    }

}
