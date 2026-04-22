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
