package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Parent configuration root for all {@link ConfigPhase#BUILD_AND_RUN_TIME_FIXED} Quarkus Flow
 * settings under the {@code quarkus.flow} prefix.
 */
@ConfigMapping(prefix = "quarkus.flow")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlowBuildFixedConfig {

    /**
     * Run task configuration.
     */
    FlowRunConfig run();

    /**
     * Dev UI configuration.
     */
    FlowDevUIConfig devui();

}
