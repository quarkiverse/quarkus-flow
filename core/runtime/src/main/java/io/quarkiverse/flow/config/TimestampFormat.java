package io.quarkiverse.flow.config;

/**
 * Timestamp format options for structured logging events.
 * <p>
 * Controls the format of timestamp fields (timestamp, startTime, endTime, lastUpdateTime)
 * in structured logging JSON output.
 */
public enum TimestampFormat {
    /**
     * ISO 8601 format with nanosecond precision (e.g., "2026-04-21T21:36:06.427832969Z").
     * <p>
     * Default format, backward compatible with existing deployments.
     */
    ISO8601,

    /**
     * Unix epoch as double with fractional seconds (e.g., 1776807366.427833).
     * <p>
     * Preserves nanosecond precision in the fractional part.
     * Compatible with PostgreSQL TIMESTAMP WITH TIME ZONE when using FluentBit pgsql output.
     */
    EPOCH_SECONDS,

    /**
     * Unix epoch milliseconds as long (e.g., 1776807366428).
     * <p>
     * Millisecond precision. Compatible with Elasticsearch @timestamp field.
     */
    EPOCH_MILLIS,

    /**
     * Unix epoch nanoseconds as long (e.g., 1776807366427832969).
     * <p>
     * Full nanosecond precision for high-precision time-series databases.
     */
    EPOCH_NANOS,

    /**
     * Custom pattern using DateTimeFormatter.
     * <p>
     * Requires {@code quarkus.flow.structured-logging.timestamp-pattern} to be set.
     * Application will fail at startup if pattern is not provided or invalid.
     */
    CUSTOM
}
