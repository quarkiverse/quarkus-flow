package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Serverless Workflow schema validation.
 */
@ConfigGroup
public interface FlowValidationConfig {

    /**
     * Input validation settings.
     */
    Input input();

    /**
     * Input schema validation configuration.
     */
    interface Input {

        /**
         * Whether to validate workflow input against the declared {@code input.schema}
         * at execution time, rejecting non-conforming input.
         * <p>
         * Disabled by default: non-conforming input is silently accepted, preserving
         * the behavior Quarkus Flow has always had.
         */
        @WithDefault("true")
        boolean enabled();

    }

}
