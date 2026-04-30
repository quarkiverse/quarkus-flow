package io.quarkiverse.flow.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.flow.structured-logging")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface FlowStructuredLoggingConfig {

    /**
     * Enables or disables structured logging for Quarkus Flow.
     * <p>
     * When enabled, all workflow lifecycle events are emitted as structured JSON logs to stdout.
     * This allows exporting workflow execution data to external databases for querying and analytics.
     * <p>
     * Default: {@code false} (users must opt-in)
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Events to capture and log.
     * <p>
     * Supports glob patterns:
     * <ul>
     * <li>{@code workflow.*} - All workflow and task events</li>
     * <li>{@code workflow.instance.*} - Only workflow-level events</li>
     * <li>{@code workflow.task.faulted} - Only task failures</li>
     * </ul>
     * <p>
     * Default: {@code workflow.*} (all events)
     */
    @WithDefault("workflow.*")
    List<String> events();

    /**
     * Include task input/output payloads in task events.
     * <p>
     * When {@code false} (default), task events only include execution metadata
     * (taskName, status, timing) but not input/output data. This keeps logs small
     * and is sufficient for execution graph visualization.
     * <p>
     * When {@code true}, task events include full input/output data, useful for
     * detailed auditing or debugging.
     * <p>
     * Default: {@code false}
     */
    @WithDefault("false")
    boolean includeTaskPayloads();

    /**
     * Include workflow input/output payloads in workflow events.
     * <p>
     * When {@code false}, workflow events only include execution metadata.
     * When {@code true} (default), workflow events include full input/output data.
     * <p>
     * Default: {@code true}
     */
    @WithDefault("true")
    boolean includeWorkflowPayloads();

    /**
     * Always include full context in error events.
     * <p>
     * When {@code true} (default), task failure and workflow failure events
     * include full input data and error details, regardless of
     * {@link #includeTaskPayloads()} setting.
     * <p>
     * This ensures errors are fully debuggable even when payload logging is disabled.
     * <p>
     * Default: {@code true}
     */
    @WithDefault("true")
    boolean includeErrorContext();

    /**
     * Max stack trace lines to include in error events.
     * <p>
     * Only read when {@link #includeErrorContext()} is {@code true}.
     * <p>
     * Default: {@code 10}
     */
    @WithDefault("10")
    int stackTraceMaxLines();

    /**
     * Maximum size (in bytes) for payloads before truncation.
     * <p>
     * Large payloads (e.g., agentic contexts with conversation history) are truncated
     * to prevent overwhelming log systems. Truncated data includes metadata about
     * original size and a preview.
     * <p>
     * Default: {@code 10240} (10KB)
     */
    @WithDefault("10240")
    int payloadMaxSize();

    /**
     * Size (in bytes) of preview included in truncated payloads.
     * <p>
     * When a payload exceeds {@link #payloadMaxSize()}, a preview of this size
     * is included to give context about the truncated data.
     * <p>
     * Default: {@code 1024} (1KB)
     */
    @WithDefault("1024")
    int truncatePreviewSize();

    /**
     * Log level for structured events.
     * <p>
     * Default: {@code INFO}
     */
    @WithDefault("INFO")
    String logLevel();

    /**
     * Timestamp format for structured logging events.
     * <p>
     * Controls the format of all timestamp fields (timestamp, startTime, endTime, lastUpdateTime).
     * <ul>
     * <li>{@code iso8601} - ISO 8601 with nanosecond precision (default, backward compatible)</li>
     * <li>{@code epoch-seconds} - Unix epoch as double with fractional seconds (PostgreSQL via FluentBit)</li>
     * <li>{@code epoch-millis} - Unix epoch milliseconds as long (Elasticsearch)</li>
     * <li>{@code epoch-nanos} - Unix epoch nanoseconds as long (high-precision time-series)</li>
     * <li>{@code custom} - Custom DateTimeFormatter pattern (requires timestamp-pattern)</li>
     * </ul>
     * <p>
     * Default: {@code iso8601}
     */
    @WithDefault("iso8601")
    TimestampFormat timestampFormat();

    /**
     * Custom timestamp pattern for DateTimeFormatter.
     * <p>
     * Only used when {@link #timestampFormat()} is {@code CUSTOM}.
     * <p>
     * Example patterns:
     * <ul>
     * <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX} - ISO 8601 with millisecond precision</li>
     * <li>{@code yyyy-MM-dd HH:mm:ss} - Simple datetime without timezone</li>
     * <li>{@code EEE, dd MMM yyyy HH:mm:ss Z} - RFC 1123 format</li>
     * </ul>
     * <p>
     * If timestamp-format is {@code CUSTOM} and this is not set or invalid,
     * the application will fail at startup with {@link IllegalArgumentException}.
     */
    Optional<String> timestampPattern();

    /**
     * Configuration for the log handler used to output structured logging events.
     * <p>
     * Controls where workflow and task execution events are written (file, stdout, or custom).
     * For containerized environments (Kubernetes, Docker), use {@code mode=container} to write to stdout.
     * <p>
     * See <a href="https://github.com/quarkiverse/quarkus-flow/issues/495">Issue #495</a> for details.
     */
    Handler handler();

    interface Handler {

        /**
         * The log handler mode to use for structured logging events.
         * <p>
         * Determines where workflow and task execution events are written:
         * <ul>
         * <li>{@code file} - Writes to a dedicated file ({@code /var/log/quarkus-flow/events.log} in production,
         * {@code target/quarkus-flow-events.log} in dev/test)</li>
         * <li>{@code container} - Writes to stdout for containerized deployments (Kubernetes, Docker, cloud-native
         * environments)</li>
         * <li>{@code none} - Disables automatic handler creation; you must manually configure handlers via
         * {@code quarkus.log.*} properties</li>
         * </ul>
         * <p>
         * <strong>Example configurations:</strong>
         *
         * <pre>
         * # File mode (default) - traditional deployments
         * quarkus.flow.structured-logging.enabled=true
         * quarkus.flow.structured-logging.handler.mode=file
         *
         * # Container mode - Kubernetes/Docker deployments
         * quarkus.flow.structured-logging.enabled=true
         * quarkus.flow.structured-logging.handler.mode=container
         *
         * # Custom mode - advanced users
         * quarkus.flow.structured-logging.enabled=true
         * quarkus.flow.structured-logging.handler.mode=none
         * # Then manually configure via quarkus.log.* properties
         * </pre>
         * <p>
         * Default: {@code file}
         *
         * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/495">Issue #495 - Handler Mode
         *      Configuration</a>
         */
        @WithDefault("file")
        Mode mode();

        enum Mode {
            /**
             * Logs workflow and task execution events to the local filesystem.
             * <p>
             * <strong>Default behavior</strong> - Creates a file handler named {@code FLOW_EVENTS} that writes to:
             * <ul>
             * <li><strong>Production:</strong> {@code /var/log/quarkus-flow/events.log}</li>
             * <li><strong>Dev/Test:</strong> {@code target/quarkus-flow-events.log}</li>
             * </ul>
             * <p>
             * <strong>Use when:</strong>
             * <ul>
             * <li>Running on traditional servers or VMs</li>
             * <li>You want events in a separate file from application logs</li>
             * <li>You're using file-based log forwarders (e.g., Filebeat, FluentBit with file input)</li>
             * </ul>
             * <p>
             * Events are formatted as raw JSON ({@code %s%n}), one event per line.
             */
            FILE,
            /**
             * Logs workflow and task execution events to stdout for containerized deployments.
             * <p>
             * Creates a console handler named {@code FLOW_EVENTS} that writes structured events to stdout.
             * Events do not appear in the parent console handler to avoid duplication.
             * <p>
             * <strong>Use when:</strong>
             * <ul>
             * <li>Running in containers (Docker, Kubernetes, Podman)</li>
             * <li>Using container runtime log collection ({@code kubectl logs}, Docker logs)</li>
             * <li>Log aggregation tools collect from stdout (FluentBit, Fluentd, Promtail)</li>
             * <li>File systems are ephemeral or read-only</li>
             * <li>Following cloud-native logging best practices</li>
             * </ul>
             * <p>
             * <strong>Benefits:</strong>
             * <ul>
             * <li>No file write permissions needed</li>
             * <li>Logs automatically captured by container runtime</li>
             * <li>Simpler configuration (3 lines vs 8 lines for manual workaround)</li>
             * <li>Works with read-only filesystems</li>
             * </ul>
             * <p>
             * Events are formatted as raw JSON ({@code %s%n}), compatible with JSON log parsers.
             * <p>
             * <strong>Example configuration:</strong>
             *
             * <pre>
             * quarkus.flow.structured-logging.enabled=true
             * quarkus.flow.structured-logging.handler.mode=container
             * quarkus.flow.structured-logging.timestamp-format=epoch-seconds
             * </pre>
             *
             * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/495">Issue #495 - Container Mode</a>
             */
            CONTAINER,
            /**
             * Disables automatic log handler creation for structured logging events.
             * <p>
             * When set to {@code NONE}, Quarkus Flow will <strong>not</strong> create any log handler
             * (file or console). Structured events are still emitted via {@code StructuredLoggingListener},
             * but you are responsible for configuring output handlers manually using standard Quarkus logging
             * properties.
             * <p>
             * <strong>Use when:</strong>
             * <ul>
             * <li>You need full control over logging configuration</li>
             * <li>You want to use custom handlers (e.g., syslog, GELF, custom formatters)</li>
             * <li>You're integrating with specialized logging infrastructure</li>
             * <li>Default handlers don't fit your deployment requirements</li>
             * </ul>
             * <p>
             * <strong>Manual configuration example:</strong>
             *
             * <pre>
             * # Disable automatic handler
             * quarkus.flow.structured-logging.enabled=true
             * quarkus.flow.structured-logging.handler.mode=none
             *
             * # Configure custom console handler manually
             * quarkus.log.handler.console."CUSTOM_FLOW".enable=true
             * quarkus.log.handler.console."CUSTOM_FLOW".format=%s%n
             * quarkus.log.category."io.quarkiverse.flow.structuredlogging".handlers=CUSTOM_FLOW
             * quarkus.log.category."io.quarkiverse.flow.structuredlogging".use-parent-handlers=false
             * </pre>
             * <p>
             * The logger category for structured events is {@code io.quarkiverse.flow.structuredlogging}.
             */
            NONE;
        }
    }
}
