package io.quarkiverse.flow.structuredlogging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
        // Note: Double precision limits mean we can only reliably verify to microsecond precision
        assertThat(fractionalPart).isCloseTo(0.123456789, within(0.000001));
    }

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
        assertThat(invokeFormatTimestamp(formatter3, timestamp)).isEqualTo("Tue, 21 Apr 2026 21:36:06 +0000");
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
        when(config.stackTraceMaxLines()).thenReturn(10);
        when(config.logLevel()).thenReturn("INFO");
        when(config.events()).thenReturn(List.of("workflow.*"));
        return config;
    }

    // Helper to invoke private formatTimestamp method via reflection
    private Object invokeFormatTimestamp(EventFormatter formatter, OffsetDateTime timestamp) throws Exception {
        Method method = EventFormatter.class.getDeclaredMethod("formatTimestamp", OffsetDateTime.class);
        method.setAccessible(true);
        return method.invoke(formatter, timestamp);
    }
}
