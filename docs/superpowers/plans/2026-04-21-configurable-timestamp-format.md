# Configurable Timestamp Format Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configurable timestamp formatting to structured logging events to support different log processors (FluentBit PostgreSQL, Elasticsearch, etc.)

**Architecture:** Extend FlowStructuredLoggingConfig with timestamp format options, add TimestampFormat enum with 5 formats (ISO8601, EPOCH_SECONDS, EPOCH_MILLIS, EPOCH_NANOS, CUSTOM), centralize formatting in EventFormatter.formatTimestamp() method with constructor-time validation.

**Tech Stack:** Java 17, Quarkus Config, Jackson ObjectMapper, JUnit 5, AssertJ, Mockito

---

## Task 1: Create TimestampFormat Enum

**Files:**
- Create: `core/runtime/src/main/java/io/quarkiverse/flow/config/TimestampFormat.java`

- [ ] **Step 1: Create TimestampFormat enum with all format options**

```java
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
```

- [ ] **Step 2: Commit TimestampFormat enum**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/config/TimestampFormat.java
git commit -m "feat: add TimestampFormat enum for configurable timestamp formats

Add enum with 5 format options:
- ISO8601 (default, backward compatible)
- EPOCH_SECONDS (PostgreSQL via FluentBit)
- EPOCH_MILLIS (Elasticsearch)
- EPOCH_NANOS (high-precision time-series)
- CUSTOM (user-defined DateTimeFormatter pattern)

Part of #473"
```

## Task 2: Add Configuration Properties

**Files:**
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/config/FlowStructuredLoggingConfig.java:120`

- [ ] **Step 1: Add timestamp format configuration properties**

Add these two methods after the `logLevel()` method (line 120):

```java
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
```

- [ ] **Step 2: Commit configuration changes**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/config/FlowStructuredLoggingConfig.java
git commit -m "feat: add timestamp format configuration to FlowStructuredLoggingConfig

Add timestampFormat() with enum type and default 'iso8601'
Add timestampPattern() for CUSTOM format

Part of #473"
```

## Task 3: Add Constructor Validation to EventFormatter

**Files:**
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java:76-79`

- [ ] **Step 1: Write failing test for constructor validation**

Create: `core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/EventFormatterValidationTest.java`

```java
package io.quarkiverse.flow.structuredlogging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.TimestampFormat;

/**
 * Unit tests for EventFormatter constructor validation.
 */
class EventFormatterValidationTest {

    @Test
    @DisplayName("constructor should throw when CUSTOM format without pattern")
    void test_constructor_throws_when_custom_format_without_pattern() {
        FlowStructuredLoggingConfig config = mock(FlowStructuredLoggingConfig.class);
        when(config.timestampFormat()).thenReturn(TimestampFormat.CUSTOM);
        when(config.timestampPattern()).thenReturn(Optional.empty());
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> new EventFormatter(config, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quarkus.flow.structured-logging.timestamp-pattern must be set")
                .hasMessageContaining("timestamp-format is 'custom'");
    }

    @Test
    @DisplayName("constructor should throw when CUSTOM format with invalid pattern")
    void test_constructor_throws_when_custom_format_with_invalid_pattern() {
        FlowStructuredLoggingConfig config = mock(FlowStructuredLoggingConfig.class);
        when(config.timestampFormat()).thenReturn(TimestampFormat.CUSTOM);
        when(config.timestampPattern()).thenReturn(Optional.of("invalid-pattern-[[["));
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> new EventFormatter(config, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timestamp pattern")
                .hasMessageContaining("invalid-pattern-[[[");
    }

    @Test
    @DisplayName("constructor should succeed when CUSTOM format with valid pattern")
    void test_constructor_succeeds_when_custom_format_with_valid_pattern() {
        FlowStructuredLoggingConfig config = mock(FlowStructuredLoggingConfig.class);
        when(config.timestampFormat()).thenReturn(TimestampFormat.CUSTOM);
        when(config.timestampPattern()).thenReturn(Optional.of("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        when(config.enabled()).thenReturn(true);
        when(config.includeWorkflowPayloads()).thenReturn(true);
        when(config.includeTaskPayloads()).thenReturn(false);
        when(config.includeErrorContext()).thenReturn(true);
        when(config.payloadMaxSize()).thenReturn(10240);
        when(config.truncatePreviewSize()).thenReturn(1024);
        ObjectMapper objectMapper = new ObjectMapper();

        // Should not throw
        new EventFormatter(config, objectMapper);
    }

    @Test
    @DisplayName("constructor should succeed for non-CUSTOM formats without pattern")
    void test_constructor_succeeds_for_non_custom_formats() {
        ObjectMapper objectMapper = new ObjectMapper();

        for (TimestampFormat format : new TimestampFormat[] {
                TimestampFormat.ISO8601,
                TimestampFormat.EPOCH_SECONDS,
                TimestampFormat.EPOCH_MILLIS,
                TimestampFormat.EPOCH_NANOS
        }) {
            FlowStructuredLoggingConfig config = mock(FlowStructuredLoggingConfig.class);
            when(config.timestampFormat()).thenReturn(format);
            when(config.timestampPattern()).thenReturn(Optional.empty());
            when(config.enabled()).thenReturn(true);
            when(config.includeWorkflowPayloads()).thenReturn(true);
            when(config.includeTaskPayloads()).thenReturn(false);
            when(config.includeErrorContext()).thenReturn(true);
            when(config.payloadMaxSize()).thenReturn(10240);
            when(config.truncatePreviewSize()).thenReturn(1024);

            // Should not throw
            new EventFormatter(config, objectMapper);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core/runtime -Dtest=EventFormatterValidationTest -DfailIfNoTests=false`
Expected: Test compilation may fail (formatTimestamp not implemented yet) or tests fail

- [ ] **Step 3: Add validation to EventFormatter constructor**

Modify the EventFormatter constructor (lines 76-79):

```java
    public EventFormatter(FlowStructuredLoggingConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        // Validate custom pattern if CUSTOM format is selected
        if (config.timestampFormat() == TimestampFormat.CUSTOM) {
            if (config.timestampPattern().isEmpty()) {
                throw new IllegalArgumentException(
                        "quarkus.flow.structured-logging.timestamp-pattern must be set when timestamp-format is 'custom'");
            }
            try {
                java.time.format.DateTimeFormatter.ofPattern(config.timestampPattern().get());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid timestamp pattern '" + config.timestampPattern().get() + "': " + e.getMessage(),
                        e);
            }
        }
    }
```

- [ ] **Step 4: Add required imports to EventFormatter**

Add to the imports section at the top of EventFormatter.java:

```java
import io.quarkiverse.flow.config.TimestampFormat;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -pl core/runtime -Dtest=EventFormatterValidationTest`
Expected: All 4 tests PASS

- [ ] **Step 6: Commit constructor validation**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java
git add core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/EventFormatterValidationTest.java
git commit -m "feat: add constructor validation for CUSTOM timestamp format

Validate that:
- CUSTOM format requires timestamp-pattern to be set
- CUSTOM pattern must be a valid DateTimeFormatter pattern
- Fail fast at startup with clear error messages

Part of #473"
```

## Task 4: Implement formatTimestamp Method (ISO8601)

**Files:**
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java`
- Create: `core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java`

- [ ] **Step 1: Write failing test for ISO8601 format**

Create: `core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java`

```java
package io.quarkiverse.flow.structuredlogging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.TimestampFormat;

/**
 * Unit tests for EventFormatter.formatTimestamp() method.
 */
class TimestampFormatterTest {

    @Test
    @DisplayName("formatTimestamp should return ISO8601 string for ISO8601 format")
    void test_format_timestamp_iso8601() throws Exception {
        FlowStructuredLoggingConfig config = createConfig(TimestampFormat.ISO8601, null);
        EventFormatter formatter = new EventFormatter(config, new ObjectMapper());

        OffsetDateTime timestamp = OffsetDateTime.of(2026, 4, 21, 21, 36, 6, 427832969, ZoneOffset.UTC);
        Object result = invokeFormatTimestamp(formatter, timestamp);

        assertThat(result).isInstanceOf(String.class);
        assertThat(result).isEqualTo("2026-04-21T21:36:06.427832969Z");
    }

    @Test
    @DisplayName("formatTimestamp should return null for null timestamp")
    void test_format_timestamp_null() throws Exception {
        FlowStructuredLoggingConfig config = createConfig(TimestampFormat.ISO8601, null);
        EventFormatter formatter = new EventFormatter(config, new ObjectMapper());

        Object result = invokeFormatTimestamp(formatter, null);

        assertThat(result).isNull();
    }

    // Helper to create mock config
    private FlowStructuredLoggingConfig createConfig(TimestampFormat format, String pattern) {
        FlowStructuredLoggingConfig config = mock(FlowStructuredLoggingConfig.class);
        when(config.timestampFormat()).thenReturn(format);
        when(config.timestampPattern()).thenReturn(Optional.ofNullable(pattern));
        when(config.enabled()).thenReturn(true);
        when(config.includeWorkflowPayloads()).thenReturn(true);
        when(config.includeTaskPayloads()).thenReturn(false);
        when(config.includeErrorContext()).thenReturn(true);
        when(config.payloadMaxSize()).thenReturn(10240);
        when(config.truncatePreviewSize()).thenReturn(1024);
        return config;
    }

    // Helper to invoke private formatTimestamp method via reflection
    private Object invokeFormatTimestamp(EventFormatter formatter, OffsetDateTime timestamp) throws Exception {
        Method method = EventFormatter.class.getDeclaredMethod("formatTimestamp", OffsetDateTime.class);
        method.setAccessible(true);
        return method.invoke(formatter, timestamp);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest`
Expected: FAIL with "java.lang.NoSuchMethodException: formatTimestamp"

- [ ] **Step 3: Add formatTimestamp method with ISO8601 support**

Add this method to EventFormatter.java after the `generateTaskExecutionId` method (around line 299):

```java
    private Object formatTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }

        switch (config.timestampFormat()) {
            case ISO8601:
                return timestamp.toString();

            default:
                return timestamp.toString();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest`
Expected: All tests PASS

- [ ] **Step 5: Commit ISO8601 implementation**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java
git add core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java
git commit -m "feat: add formatTimestamp method with ISO8601 support

Add private formatTimestamp() method with:
- Null handling
- ISO8601 format (default)
- Switch statement for future format additions

Part of #473"
```

## Task 5: Implement EPOCH_SECONDS Format

**Files:**
- Modify: `core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java`
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java`

- [ ] **Step 1: Write failing test for EPOCH_SECONDS format**

Add to TimestampFormatterTest.java:

```java
    @Test
    @DisplayName("formatTimestamp should return double for EPOCH_SECONDS format")
    void test_format_timestamp_epoch_seconds() throws Exception {
        FlowStructuredLoggingConfig config = createConfig(TimestampFormat.EPOCH_SECONDS, null);
        EventFormatter formatter = new EventFormatter(config, new ObjectMapper());

        OffsetDateTime timestamp = OffsetDateTime.of(2026, 4, 21, 21, 36, 6, 427832969, ZoneOffset.UTC);
        Object result = invokeFormatTimestamp(formatter, timestamp);

        assertThat(result).isInstanceOf(Double.class);
        double epochSeconds = (Double) result;
        // 2026-04-21T21:36:06.427832969Z = 1776807366 seconds + 0.427832969 fractional
        assertThat(epochSeconds).isCloseTo(1776807366.427833, within(0.000001));
    }

    @Test
    @DisplayName("formatTimestamp should preserve nanosecond precision in EPOCH_SECONDS")
    void test_format_timestamp_epoch_seconds_preserves_nanosecond_precision() throws Exception {
        FlowStructuredLoggingConfig config = createConfig(TimestampFormat.EPOCH_SECONDS, null);
        EventFormatter formatter = new EventFormatter(config, new ObjectMapper());

        // Test with specific nanosecond values
        OffsetDateTime timestamp = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 123456789, ZoneOffset.UTC);
        Object result = invokeFormatTimestamp(formatter, timestamp);

        double epochSeconds = (Double) result;
        long epochSecondsPart = (long) epochSeconds;
        double fractionalPart = epochSeconds - epochSecondsPart;

        // Fractional part should be 123456789 / 1_000_000_000 = 0.123456789
        assertThat(fractionalPart).isCloseTo(0.123456789, within(0.000000001));
    }
```

Add static import at the top:

```java
import static org.assertj.core.api.Assertions.within;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest`
Expected: FAIL with assertion error (default returns String, not Double)

- [ ] **Step 3: Implement EPOCH_SECONDS in formatTimestamp**

Update the formatTimestamp method in EventFormatter.java:

```java
    private Object formatTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }

        switch (config.timestampFormat()) {
            case ISO8601:
                return timestamp.toString();

            case EPOCH_SECONDS:
                java.time.Instant instant = timestamp.toInstant();
                double seconds = instant.getEpochSecond() + (instant.getNano() / 1_000_000_000.0);
                return seconds;

            default:
                return timestamp.toString();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest`
Expected: All tests PASS

- [ ] **Step 5: Commit EPOCH_SECONDS implementation**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java
git add core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java
git commit -m "feat: add EPOCH_SECONDS timestamp format

Return double with fractional seconds
Preserve nanosecond precision in fractional part
Compatible with PostgreSQL TIMESTAMP WITH TIME ZONE

Part of #473"
```

## Task 6: Implement EPOCH_MILLIS Format

**Files:**
- Modify: `core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java`
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java`

- [ ] **Step 1: Write failing test for EPOCH_MILLIS format**

Add to TimestampFormatterTest.java:

```java
    @Test
    @DisplayName("formatTimestamp should return long for EPOCH_MILLIS format")
    void test_format_timestamp_epoch_millis() throws Exception {
        FlowStructuredLoggingConfig config = createConfig(TimestampFormat.EPOCH_MILLIS, null);
        EventFormatter formatter = new EventFormatter(config, new ObjectMapper());

        OffsetDateTime timestamp = OffsetDateTime.of(2026, 4, 21, 21, 36, 6, 427832969, ZoneOffset.UTC);
        Object result = invokeFormatTimestamp(formatter, timestamp);

        assertThat(result).isInstanceOf(Long.class);
        // 2026-04-21T21:36:06.427Z = 1776807366427 milliseconds
        assertThat(result).isEqualTo(1776807366427L);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest#test_format_timestamp_epoch_millis`
Expected: FAIL with assertion error

- [ ] **Step 3: Implement EPOCH_MILLIS in formatTimestamp**

Update the formatTimestamp method in EventFormatter.java:

```java
    private Object formatTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }

        switch (config.timestampFormat()) {
            case ISO8601:
                return timestamp.toString();

            case EPOCH_SECONDS:
                java.time.Instant instant = timestamp.toInstant();
                double seconds = instant.getEpochSecond() + (instant.getNano() / 1_000_000_000.0);
                return seconds;

            case EPOCH_MILLIS:
                return timestamp.toInstant().toEpochMilli();

            default:
                return timestamp.toString();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest`
Expected: All tests PASS

- [ ] **Step 5: Commit EPOCH_MILLIS implementation**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java
git add core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java
git commit -m "feat: add EPOCH_MILLIS timestamp format

Return long with millisecond precision
Compatible with Elasticsearch @timestamp field

Part of #473"
```

## Task 7: Implement EPOCH_NANOS Format

**Files:**
- Modify: `core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java`
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java`

- [ ] **Step 1: Write failing test for EPOCH_NANOS format**

Add to TimestampFormatterTest.java:

```java
    @Test
    @DisplayName("formatTimestamp should return long for EPOCH_NANOS format")
    void test_format_timestamp_epoch_nanos() throws Exception {
        FlowStructuredLoggingConfig config = createConfig(TimestampFormat.EPOCH_NANOS, null);
        EventFormatter formatter = new EventFormatter(config, new ObjectMapper());

        OffsetDateTime timestamp = OffsetDateTime.of(2026, 4, 21, 21, 36, 6, 427832969, ZoneOffset.UTC);
        Object result = invokeFormatTimestamp(formatter, timestamp);

        assertThat(result).isInstanceOf(Long.class);
        // 1776807366 seconds * 1_000_000_000 + 427832969 nanos = 1776807366427832969
        assertThat(result).isEqualTo(1776807366427832969L);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest#test_format_timestamp_epoch_nanos`
Expected: FAIL with assertion error

- [ ] **Step 3: Implement EPOCH_NANOS in formatTimestamp**

Update the formatTimestamp method in EventFormatter.java:

```java
    private Object formatTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }

        switch (config.timestampFormat()) {
            case ISO8601:
                return timestamp.toString();

            case EPOCH_SECONDS:
                java.time.Instant instant = timestamp.toInstant();
                double seconds = instant.getEpochSecond() + (instant.getNano() / 1_000_000_000.0);
                return seconds;

            case EPOCH_MILLIS:
                return timestamp.toInstant().toEpochMilli();

            case EPOCH_NANOS:
                java.time.Instant inst = timestamp.toInstant();
                return inst.getEpochSecond() * 1_000_000_000L + inst.getNano();

            default:
                return timestamp.toString();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest`
Expected: All tests PASS

- [ ] **Step 5: Commit EPOCH_NANOS implementation**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java
git add core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java
git commit -m "feat: add EPOCH_NANOS timestamp format

Return long with full nanosecond precision
Compatible with high-precision time-series databases

Part of #473"
```

## Task 8: Implement CUSTOM Format

**Files:**
- Modify: `core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java`
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java`

- [ ] **Step 1: Write failing test for CUSTOM format**

Add to TimestampFormatterTest.java:

```java
    @Test
    @DisplayName("formatTimestamp should use custom pattern for CUSTOM format")
    void test_format_timestamp_custom() throws Exception {
        FlowStructuredLoggingConfig config = createConfig(TimestampFormat.CUSTOM, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        EventFormatter formatter = new EventFormatter(config, new ObjectMapper());

        OffsetDateTime timestamp = OffsetDateTime.of(2026, 4, 21, 21, 36, 6, 427832969, ZoneOffset.UTC);
        Object result = invokeFormatTimestamp(formatter, timestamp);

        assertThat(result).isInstanceOf(String.class);
        // Custom pattern truncates to milliseconds
        assertThat(result).isEqualTo("2026-04-21T21:36:06.427Z");
    }

    @Test
    @DisplayName("formatTimestamp should support various custom patterns")
    void test_format_timestamp_custom_patterns() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.of(2026, 4, 21, 21, 36, 6, 427832969, ZoneOffset.UTC);

        // Pattern 1: Date only
        FlowStructuredLoggingConfig config1 = createConfig(TimestampFormat.CUSTOM, "yyyy-MM-dd");
        EventFormatter formatter1 = new EventFormatter(config1, new ObjectMapper());
        assertThat(invokeFormatTimestamp(formatter1, timestamp)).isEqualTo("2026-04-21");

        // Pattern 2: Simple datetime without timezone
        FlowStructuredLoggingConfig config2 = createConfig(TimestampFormat.CUSTOM, "yyyy-MM-dd HH:mm:ss");
        EventFormatter formatter2 = new EventFormatter(config2, new ObjectMapper());
        assertThat(invokeFormatTimestamp(formatter2, timestamp)).isEqualTo("2026-04-21 21:36:06");

        // Pattern 3: RFC 1123 style
        FlowStructuredLoggingConfig config3 = createConfig(TimestampFormat.CUSTOM, "EEE, dd MMM yyyy HH:mm:ss Z");
        EventFormatter formatter3 = new EventFormatter(config3, new ObjectMapper());
        assertThat(invokeFormatTimestamp(formatter3, timestamp)).isEqualTo("Mon, 21 Apr 2026 21:36:06 +0000");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest#test_format_timestamp_custom`
Expected: FAIL with assertion error

- [ ] **Step 3: Implement CUSTOM in formatTimestamp**

Update the formatTimestamp method in EventFormatter.java:

```java
    private Object formatTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }

        switch (config.timestampFormat()) {
            case ISO8601:
                return timestamp.toString();

            case EPOCH_SECONDS:
                java.time.Instant instant = timestamp.toInstant();
                double seconds = instant.getEpochSecond() + (instant.getNano() / 1_000_000_000.0);
                return seconds;

            case EPOCH_MILLIS:
                return timestamp.toInstant().toEpochMilli();

            case EPOCH_NANOS:
                java.time.Instant inst = timestamp.toInstant();
                return inst.getEpochSecond() * 1_000_000_000L + inst.getNano();

            case CUSTOM:
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern(
                        config.timestampPattern().get());
                return timestamp.format(formatter);

            default:
                return timestamp.toString();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl core/runtime -Dtest=TimestampFormatterTest`
Expected: All tests PASS

- [ ] **Step 5: Commit CUSTOM implementation**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java
git add core/runtime/src/test/java/io/quarkiverse/flow/structuredlogging/TimestampFormatterTest.java
git commit -m "feat: add CUSTOM timestamp format with DateTimeFormatter

Support user-defined patterns with full DateTimeFormatter syntax
Pattern validation happens at constructor time (fail-fast)

Part of #473"
```

## Task 9: Update EventFormatter to Use formatTimestamp

**Files:**
- Modify: `core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java`

- [ ] **Step 1: Update formatWorkflowStarted method**

Replace line 91 in EventFormatter.java:

Old:
```java
        json.put(FIELD_START_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_START_TIME, formatTimestamp(event.eventDate()));
```

Also update the timestamp field (line 278 in baseWorkflowEvent):

Old:
```java
        json.put(FIELD_TIMESTAMP, event.eventDate());
```

New:
```java
        json.put(FIELD_TIMESTAMP, formatTimestamp(event.eventDate()));
```

- [ ] **Step 2: Update formatWorkflowCompleted method**

Replace line 104:

Old:
```java
        json.put(FIELD_END_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 3: Update formatWorkflowFailed method**

Replace line 115:

Old:
```java
        json.put(FIELD_END_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 4: Update formatWorkflowCancelled method**

Replace line 175:

Old:
```java
        json.put(FIELD_END_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 5: Update formatWorkflowStatusChanged method**

Replace line 194:

Old:
```java
        json.put(FIELD_LAST_UPDATE_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_LAST_UPDATE_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 6: Update formatTaskStarted method**

Replace line 203:

Old:
```java
        json.put(FIELD_START_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_START_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 7: Update formatTaskCompleted method**

Replace line 216:

Old:
```java
        json.put(FIELD_END_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 8: Update formatTaskFailed method**

Replace line 229:

Old:
```java
        json.put(FIELD_END_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 9: Update formatTaskCancelled method**

Replace line 250:

Old:
```java
        json.put(FIELD_END_TIME, event.eventDate());
```

New:
```java
        json.put(FIELD_END_TIME, formatTimestamp(event.eventDate()));
```

- [ ] **Step 10: Run all EventFormatter tests**

Run: `./mvnw test -pl core/runtime -Dtest=*EventFormatter*,TimestampFormatterTest`
Expected: All tests PASS

- [ ] **Step 11: Commit formatTimestamp integration**

```bash
git add core/runtime/src/main/java/io/quarkiverse/flow/structuredlogging/EventFormatter.java
git commit -m "feat: integrate formatTimestamp into all event formatter methods

Replace direct event.eventDate() calls with formatTimestamp()
Affects 13 methods and 10 timestamp fields:
- baseWorkflowEvent: timestamp
- formatWorkflowStarted: startTime
- formatWorkflowCompleted: endTime
- formatWorkflowFailed: endTime
- formatWorkflowCancelled: endTime
- formatWorkflowStatusChanged: lastUpdateTime
- formatTaskStarted: startTime
- formatTaskCompleted: endTime
- formatTaskFailed: endTime
- formatTaskCancelled: endTime

Part of #473"
```

## Task 10: Add Integration Tests

**Files:**
- Modify: `core/integration-tests/src/test/java/io/quarkiverse/flow/it/StructuredLoggingTest.java`

- [ ] **Step 1: Add test for EPOCH_SECONDS format**

Add to StructuredLoggingTest.java before the inner class:

```java
    @Test
    void testEpochSecondsTimestampFormat() {
        // Execute workflow with epoch-seconds format
        helloWorkflow.startInstance().await().indefinitely();

        // Manual verification: Check build logs for JSON events with numeric timestamps like:
        // {"eventType":"io.serverlessworkflow.workflow.started.v1","timestamp":1776807366.427833,...}
    }
```

- [ ] **Step 2: Add test profile for EPOCH_SECONDS**

Add inner class after EnableStructuredLogging:

```java
    @QuarkusTest
    @TestProfile(StructuredLoggingTest.EnableEpochSecondsFormat.class)
    public static class EpochSecondsFormatTest {

        @Inject
        HelloWorkflow helloWorkflow;

        @Test
        void testEpochSecondsTimestampFormat() {
            helloWorkflow.startInstance().await().indefinitely();
        }
    }

    public static class EnableEpochSecondsFormat implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.timestamp-format", "epoch-seconds",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }
```

- [ ] **Step 3: Add test profile for EPOCH_MILLIS**

Add inner class:

```java
    @QuarkusTest
    @TestProfile(StructuredLoggingTest.EnableEpochMillisFormat.class)
    public static class EpochMillisFormatTest {

        @Inject
        HelloWorkflow helloWorkflow;

        @Test
        void testEpochMillisTimestampFormat() {
            helloWorkflow.startInstance().await().indefinitely();
        }
    }

    public static class EnableEpochMillisFormat implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.timestamp-format", "epoch-millis",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }
```

- [ ] **Step 4: Add test profile for CUSTOM format**

Add inner class:

```java
    @QuarkusTest
    @TestProfile(StructuredLoggingTest.EnableCustomTimestampFormat.class)
    public static class CustomTimestampFormatTest {

        @Inject
        HelloWorkflow helloWorkflow;

        @Test
        void testCustomTimestampFormat() {
            helloWorkflow.startInstance().await().indefinitely();
        }
    }

    public static class EnableCustomTimestampFormat implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flow.structured-logging.enabled", "true",
                    "quarkus.flow.structured-logging.timestamp-format", "custom",
                    "quarkus.flow.structured-logging.timestamp-pattern", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "quarkus.flow.structured-logging.log-level", "INFO");
        }
    }
```

- [ ] **Step 5: Run integration tests**

Run: `./mvnw test -pl core/integration-tests -Dtest=StructuredLoggingTest`
Expected: All tests PASS

- [ ] **Step 6: Commit integration tests**

```bash
git add core/integration-tests/src/test/java/io/quarkiverse/flow/it/StructuredLoggingTest.java
git commit -m "test: add integration tests for timestamp formats

Add test profiles for:
- EPOCH_SECONDS format
- EPOCH_MILLIS format
- CUSTOM format with pattern

Manual verification via build logs required

Part of #473"
```

## Task 11: Update Documentation

**Files:**
- Modify: `docs/modules/ROOT/pages/structured-logging.adoc`

- [ ] **Step 1: Read current documentation**

Run: `head -100 docs/modules/ROOT/pages/structured-logging.adoc`

- [ ] **Step 2: Add Timestamp Format section**

Add a new section after the "Configuration" section in structured-logging.adoc:

```asciidoc
== Timestamp Format

By default, structured logging events use ISO 8601 timestamp format with nanosecond precision (e.g., `2026-04-21T21:36:06.427832969Z`). You can configure the format to match your log processor's requirements.

=== Available Formats

[cols="1,2,1"]
|===
|Format |Description |Example

|`iso8601`
|ISO 8601 with nanosecond precision (default)
|`"2026-04-21T21:36:06.427832969Z"`

|`epoch-seconds`
|Unix epoch as double with fractional seconds
|`1776807366.427833`

|`epoch-millis`
|Unix epoch milliseconds as long
|`1776807366428`

|`epoch-nanos`
|Unix epoch nanoseconds as long
|`1776807366427832969`

|`custom`
|Custom DateTimeFormatter pattern
|User-defined
|===

=== Configuration

[source,properties]
----
# Default: ISO 8601 (backward compatible)
quarkus.flow.structured-logging.timestamp-format=iso8601

# Unix epoch with fractional seconds (PostgreSQL via FluentBit)
quarkus.flow.structured-logging.timestamp-format=epoch-seconds

# Unix epoch milliseconds (Elasticsearch)
quarkus.flow.structured-logging.timestamp-format=epoch-millis

# Unix epoch nanoseconds (high-precision time-series)
quarkus.flow.structured-logging.timestamp-format=epoch-nanos

# Custom format
quarkus.flow.structured-logging.timestamp-format=custom
quarkus.flow.structured-logging.timestamp-pattern=yyyy-MM-dd'T'HH:mm:ss.SSSXXX
----

=== Use Case: FluentBit → PostgreSQL

When using FluentBit's `pgsql` output plugin to insert structured logs into PostgreSQL `TIMESTAMP WITH TIME ZONE` columns, use `epoch-seconds` format:

[source,properties]
----
quarkus.flow.structured-logging.enabled=true
quarkus.flow.structured-logging.timestamp-format=epoch-seconds
----

FluentBit configuration:
[source,conf]
----
[OUTPUT]
    Name pgsql
    Match *
    Host postgres.example.com
    Database workflow_logs
    Table events
    # timestamp column is TIMESTAMP WITH TIME ZONE
    # epoch-seconds format inserts directly without Lua preprocessing
----

Event output:
[source,json]
----
{
  "eventType": "io.serverlessworkflow.workflow.started.v1",
  "timestamp": 1776807366.427833,
  "startTime": 1776807366.427833,
  "instanceId": "abc123",
  "workflowName": "payment-processing"
}
----

=== Precision Handling

[cols="1,2"]
|===
|Format |Precision

|`iso8601`
|Nanosecond (preserved in string)

|`epoch-seconds`
|Nanosecond (fractional part)

|`epoch-millis`
|Millisecond

|`epoch-nanos`
|Nanosecond

|`custom`
|Depends on pattern
|===

=== Custom Pattern Examples

[source,properties]
----
# ISO 8601 with millisecond precision
quarkus.flow.structured-logging.timestamp-pattern=yyyy-MM-dd'T'HH:mm:ss.SSSXXX

# Simple datetime without timezone
quarkus.flow.structured-logging.timestamp-pattern=yyyy-MM-dd HH:mm:ss

# RFC 1123 format
quarkus.flow.structured-logging.timestamp-pattern=EEE, dd MMM yyyy HH:mm:ss Z

# Date only
quarkus.flow.structured-logging.timestamp-pattern=yyyy-MM-dd
----

NOTE: When using `custom` format, the `timestamp-pattern` property is required. The application will fail at startup if the pattern is missing or invalid.
```

- [ ] **Step 3: Verify documentation renders correctly**

Run: `./mvnw -pl docs quarkus:dev`
Press 'w' when Quarkus starts, navigate to structured-logging page, verify new section appears

- [ ] **Step 4: Commit documentation**

```bash
git add docs/modules/ROOT/pages/structured-logging.adoc
git commit -m "docs: add timestamp format configuration section

Document all 5 timestamp formats with:
- Format descriptions and examples
- Configuration properties
- FluentBit PostgreSQL use case
- Precision handling table
- Custom pattern examples

Part of #473"
```

## Task 12: Run Full Build and Integration Tests

**Files:**
- None (validation only)

- [ ] **Step 1: Run full build with all tests**

Run: `./mvnw clean install -DskipITs=false`
Expected: BUILD SUCCESS with all tests passing

- [ ] **Step 2: Verify unit tests**

Check test output for:
- EventFormatterValidationTest: 4 tests
- TimestampFormatterTest: 8 tests
- StructuredLoggingListenerTest: existing tests still pass

- [ ] **Step 3: Verify integration tests**

Check test output for:
- StructuredLoggingTest: 4 test classes (default + 3 new formats)

- [ ] **Step 4: Check for warnings or errors**

Review build log for any deprecation warnings or compilation issues

- [ ] **Step 5: Test documentation build**

Run: `./mvnw -pl docs clean compile`
Expected: Documentation builds without AsciiDoc errors

## Task 13: Final Verification and Cleanup

**Files:**
- None (validation only)

- [ ] **Step 1: Verify all timestamp formats work end-to-end**

For each format, check build logs contain expected timestamp format:
- ISO8601: String format
- EPOCH_SECONDS: Decimal number
- EPOCH_MILLIS: Integer number
- CUSTOM: Formatted string

- [ ] **Step 2: Verify backward compatibility**

Check that default behavior (iso8601) matches previous output format

- [ ] **Step 3: Review git log**

Run: `git log --oneline feature/473-configurable-timestamp-format`
Expected: 13 commits (1 enum + 1 config + 1 validation + 5 formats + 1 integration + 1 tests + 1 docs + 1 update methods)

- [ ] **Step 4: Final commit count check**

Count commits: should have approximately 13 focused commits

---

## Implementation Complete

At this point, the feature is fully implemented with:
- ✅ TimestampFormat enum with 5 options
- ✅ Configuration properties (timestampFormat, timestampPattern)
- ✅ Constructor validation for CUSTOM format
- ✅ formatTimestamp() method with all formats
- ✅ Integration into all 13 event formatter methods
- ✅ Comprehensive unit tests (12 tests total)
- ✅ Integration tests (4 test profiles)
- ✅ Complete documentation with examples
- ✅ Backward compatibility preserved (iso8601 default)

Ready for code review and PR creation per CLAUDE.md requirements.
