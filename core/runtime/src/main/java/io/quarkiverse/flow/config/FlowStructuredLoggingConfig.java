package io.quarkiverse.flow.config;

import java.util.List;

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
}
