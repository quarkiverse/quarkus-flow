package io.quarkiverse.flow.durable.kube.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.flow.durable.kube.dev")
public interface DevModeConfig {

    public static final String DEV_MODE_ENABLED_CONFIG = "quarkus.flow.durable.kube.dev.dev-mode-strategy-enabled";

    /**
     * If true, enables the dev-mode/test-mode strategy that avoids querying the K8s cluster.
     * Defaults to true.
     */
    @WithDefault("true")
    Boolean devModeStrategyEnabled();
}
