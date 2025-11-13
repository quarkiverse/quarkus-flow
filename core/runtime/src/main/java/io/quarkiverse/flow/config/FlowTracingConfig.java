package io.quarkiverse.flow.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.flow.tracing")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface FlowTracingConfig {

    /**
     * Whether to enable workflow execution tracing.
     * If not set, it defaults to true in dev and test mode.
     */
    Optional<Boolean> enabled();

}
