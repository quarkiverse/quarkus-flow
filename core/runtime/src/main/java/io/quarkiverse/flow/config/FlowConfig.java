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
public interface FlowConfig {

    /**
     * Secrets configuration.
     */
    FlowSecretsConfig secrets();

    /**
     * Structured logging configuration.
     */
    FlowStructuredLoggingConfig structuredLogging();

    /**
     * Serverless Workflow schema validation configuration.
     */
    FlowValidationConfig validation();

    /**
     * Metrics configuration.
     */
    FlowMetricsConfig metrics();

    /**
     * HTTP/OpenAPI client configuration.
     */
    Http http();

    /**
     * Tracing configuration.
     */
    FlowTracingConfig tracing();

    /**
     * Groups the HTTP client configuration under {@code quarkus.flow.http.client}.
     */
    interface Http {

        /**
         * HTTP/OpenAPI client configuration.
         */
        FlowHttpConfig client();

    }

}
