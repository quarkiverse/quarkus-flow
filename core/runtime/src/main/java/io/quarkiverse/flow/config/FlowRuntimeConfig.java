package io.quarkiverse.flow.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Parent configuration root for all run-time ({@link ConfigPhase#RUN_TIME}) Quarkus Flow
 * settings under the {@code quarkus.flow} prefix.
 */
@ConfigMapping(prefix = "quarkus.flow")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowRuntimeConfig {

    String FLOW_PREFIX = "quarkus.flow";

    /**
     * Serverless Workflow schema validation configuration.
     */
    FlowValidationConfig validation();

}
