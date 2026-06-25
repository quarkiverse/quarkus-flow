package io.quarkiverse.flow.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for Quarkus Flow metrics collection.
 * <p>
 * <strong>Note:</strong> Changes to metrics configuration require an application restart to take effect.
 * The metrics listener is registered at startup and cannot be dynamically added or removed.
 * <p>
 * To enable metrics:
 *
 * <pre>
 * <code>quarkus.flow.metrics.enabled=true</code>
 * </pre>
 *
 * Then restart the application (no rebuild required).
 */
@ConfigMapping(prefix = "quarkus.flow.metrics")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowMetricsConfig {

    /**
     * Default prefix applied to all exported metrics.
     */
    String DEFAULT_PREFIX = "quarkus.flow";

    /**
     * Enables or disables metrics collection for Quarkus Flow.
     * <p>
     * When set to {@code false}, no metrics are published.
     * <p>
     * <strong>Restart required:</strong> Changing this property requires an application restart to take effect.
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
     * <strong>Restart required:</strong> Changing this property requires an application restart to take effect.
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
         * Enables or disables the duration <em>distribution statistics</em>
         * (the histogram and the configured percentiles).
         * <p>
         * The duration {@code Timer} is always registered and recorded, so its base
         * statistics (count, total time, and max) are published regardless of this
         * setting. When set to {@code false}, only the histogram and percentiles are
         * omitted; the exact representation of these statistics in the backend depends
         * on the configured Micrometer registry (for example, Prometheus or an
         * OpenTelemetry bridge).
         * <p>
         * <strong>Restart required:</strong> Changing this property requires an application restart to take effect.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Percentiles to be calculated for duration metrics.
         * <p>
         * Values must be in the range {@code (0, 1)}, for example:
         * {@code 0.5}, {@code 0.95}, {@code 0.99}.
         * <p>
         * <strong>Restart required:</strong> Changing this property requires an application restart to take effect.
         */
        Optional<List<String>> percentiles();

    }
}
