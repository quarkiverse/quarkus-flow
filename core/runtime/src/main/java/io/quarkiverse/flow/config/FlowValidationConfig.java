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
         * Enabled by default: a declared {@code input.schema} is a real contract. Set to
         * {@code false} to restore the pre-1.3.0 behavior, where non-conforming input was
         * silently accepted.
         */
        @WithDefault("true")
        boolean enabled();

    }

}
