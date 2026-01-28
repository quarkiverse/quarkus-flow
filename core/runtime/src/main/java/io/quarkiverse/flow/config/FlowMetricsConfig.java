package io.quarkiverse.flow.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.metrics")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlowMetricsConfig {

    /**
     * Default prefix applied to all exported metrics.
     */
    String DEFAULT_PREFIX = "quarkus.flow";

    /**
     * Enables or disables metrics collection for Quarkus Flow.
     * <p>
     * When set to {@code false}, no metrics are published.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Prefix applied to all exported metric names.
     * <p>
     * This allows distinguishing Quarkus Flow metrics from other
     * application or framework metrics.
     * <p>
     * Please, configure a prefix separating words by (<code>.</code>) dot character.
     * <p>
     * {@see <a href="https://quarkus.io/guides/telemetry-micrometer#naming-conventions">Naming conventions</a>}.
     */
    @WithDefault(DEFAULT_PREFIX)
    Optional<String> prefix();

    /**
     * Configuration related to duration-based metrics
     * (for example, workflow and task execution times).
     */
    Durations durations();

    interface Durations {

        /**
         * Enables or disables duration metrics.
         * <p>
         * When disabled, no timing metrics (such as workflow or task
         * execution durations) are published.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Percentiles to be calculated for duration metrics.
         * <p>
         * Values must be in the range {@code (0, 1)}, for example:
         * {@code 0.5}, {@code 0.95}, {@code 0.99}.
         */
        Optional<List<String>> percentiles();

    }
}
