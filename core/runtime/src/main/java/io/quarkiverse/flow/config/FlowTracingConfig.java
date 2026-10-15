package io.quarkiverse.flow.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface FlowTracingConfig {

    /**
     * Whether to enable workflow execution tracing.
     * If not set, it defaults to true in dev and test mode.
     */
    Optional<Boolean> enabled();

}
