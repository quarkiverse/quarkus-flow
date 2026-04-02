package io.quarkiverse.flow.durable.kube.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.flow.durable.kube.dev")
public interface DevModeConfig {

    /**
     * If true, enables the dev-mode/test-mode strategy that avoids querying the K8s cluster when in dev mode or testing
     * profiles.
     * Defaults to true.
     * <p/>
     * Enable it ONLY if you can mock the Kubernetes objects required for the durable use case.
     * In normal conditions, you will NEVER disable this property.
     */
    @WithDefault("true")
    boolean devModeStrategyEnabled();
}
