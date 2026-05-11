package io.quarkiverse.flow.opentelemetry.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.flow.otel")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowOTelConfig {

    /**
     * Enable OpenTelemetry for Quarkus Flows.
     */
    Optional<Boolean> enabled();

    /**
     * Use this method to access the actual configured value, or default value.
     * To distinguish default value from user explicitly configured value, if any, use enabled() instead.
     *
     * @return returns the currently configured value or the default value when not configured.
     */
    default boolean isEnabled() {
        return enabled().orElse(true);
    }
}
