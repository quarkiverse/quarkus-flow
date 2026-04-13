package io.quarkiverse.flow.structuredlogging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;

/**
 * Unit tests for {@link StructuredLoggingListener} pattern matching logic.
 */
public class StructuredLoggingListenerTest {

    private FlowStructuredLoggingConfig config;
    private ObjectMapper objectMapper;
    private StructuredLoggingListener listener;

    @BeforeEach
    void setUp() {
        config = mock(FlowStructuredLoggingConfig.class);
        objectMapper = new ObjectMapper();

        when(config.enabled()).thenReturn(true);
        when(config.includeWorkflowPayloads()).thenReturn(true);
        when(config.includeTaskPayloads()).thenReturn(false);
        when(config.includeErrorContext()).thenReturn(true);
        when(config.payloadMaxSize()).thenReturn(10240);
        when(config.truncatePreviewSize()).thenReturn(1024);
        when(config.logLevel()).thenReturn("INFO");
    }

    /**
     * Test data for pattern matching.
     */
    private static Stream<Arguments> providePatternMatchingData() {
        return Stream.of(
                // Pattern "*" matches everything
                arguments("*", "workflow.instance.started", true),
                arguments("*", "workflow.task.failed", true),
                arguments("*", "anything.else", true),

                // Pattern "workflow.*" matches all workflow events
                arguments("workflow.*", "workflow.instance.started", true),
                arguments("workflow.*", "workflow.task.completed", true),
                arguments("workflow.*", "other.event", false),

                // Pattern "workflow.instance.*" matches only workflow instance events
                arguments("workflow.instance.*", "workflow.instance.started", true),
                arguments("workflow.instance.*", "workflow.instance.completed", true),
                arguments("workflow.instance.*", "workflow.instance.failed", true),
                arguments("workflow.instance.*", "workflow.task.started", false),
                arguments("workflow.instance.*", "workflow.task.failed", false),

                // Pattern "workflow.task.*" matches only task events
                arguments("workflow.task.*", "workflow.task.started", true),
                arguments("workflow.task.*", "workflow.task.completed", true),
                arguments("workflow.task.*", "workflow.task.failed", true),
                arguments("workflow.task.*", "workflow.instance.started", false),

                // Exact match
                arguments("workflow.task.failed", "workflow.task.failed", true),
                arguments("workflow.task.failed", "workflow.task.completed", false),
                arguments("workflow.task.failed", "workflow.instance.failed", false),

                // Multiple specific events
                arguments("workflow.instance.failed", "workflow.instance.failed", true),
                arguments("workflow.instance.failed", "workflow.instance.started", false));
    }

    @ParameterizedTest(name = "{index} => pattern=''{0}'', eventType=''{1}'', shouldMatch={2}")
    @MethodSource("providePatternMatchingData")
    @DisplayName("shouldLog should correctly match event patterns")
    void testPatternMatching(String pattern, String eventType, boolean shouldMatch) throws Exception {
        when(config.events()).thenReturn(List.of(pattern));
        listener = new StructuredLoggingListener(config, objectMapper);

        boolean result = invokeShouldLog(listener, eventType);

        assertThat(result).isEqualTo(shouldMatch);
    }

    @Test
    @DisplayName("shouldLog should return false when disabled")
    void testShouldLogWhenDisabled() throws Exception {
        when(config.enabled()).thenReturn(false);
        when(config.events()).thenReturn(List.of("workflow.*"));
        listener = new StructuredLoggingListener(config, objectMapper);

        boolean result = invokeShouldLog(listener, "workflow.instance.started");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("shouldLog should match against multiple patterns")
    void testMultiplePatterns() throws Exception {
        when(config.events()).thenReturn(List.of("workflow.instance.failed", "workflow.task.failed"));
        listener = new StructuredLoggingListener(config, objectMapper);

        assertThat(invokeShouldLog(listener, "workflow.instance.failed")).isTrue();
        assertThat(invokeShouldLog(listener, "workflow.task.failed")).isTrue();
        assertThat(invokeShouldLog(listener, "workflow.instance.started")).isFalse();
        assertThat(invokeShouldLog(listener, "workflow.task.started")).isFalse();
    }

    @Test
    @DisplayName("shouldLog should match if any pattern matches")
    void testAnyPatternMatches() throws Exception {
        when(config.events()).thenReturn(List.of("workflow.instance.*", "workflow.task.failed"));
        listener = new StructuredLoggingListener(config, objectMapper);

        // Matches first pattern
        assertThat(invokeShouldLog(listener, "workflow.instance.started")).isTrue();
        assertThat(invokeShouldLog(listener, "workflow.instance.completed")).isTrue();

        // Matches second pattern
        assertThat(invokeShouldLog(listener, "workflow.task.failed")).isTrue();

        // Matches neither
        assertThat(invokeShouldLog(listener, "workflow.task.started")).isFalse();
        assertThat(invokeShouldLog(listener, "workflow.task.completed")).isFalse();
    }

    @Test
    @DisplayName("shouldLog should handle empty pattern list")
    void testEmptyPatternList() throws Exception {
        when(config.events()).thenReturn(List.of());
        listener = new StructuredLoggingListener(config, objectMapper);

        boolean result = invokeShouldLog(listener, "workflow.instance.started");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Event type constants should match expected values")
    void testEventTypeConstants() {
        assertThat(StructuredLoggingEventTypes.WORKFLOW_INSTANCE_STARTED)
                .isEqualTo("workflow.instance.started");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_INSTANCE_COMPLETED)
                .isEqualTo("workflow.instance.completed");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_INSTANCE_FAILED)
                .isEqualTo("workflow.instance.failed");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_INSTANCE_CANCELLED)
                .isEqualTo("workflow.instance.cancelled");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_INSTANCE_SUSPENDED)
                .isEqualTo("workflow.instance.suspended");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_INSTANCE_RESUMED)
                .isEqualTo("workflow.instance.resumed");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_INSTANCE_STATUS_CHANGED)
                .isEqualTo("workflow.instance.status.changed");

        assertThat(StructuredLoggingEventTypes.WORKFLOW_TASK_STARTED)
                .isEqualTo("workflow.task.started");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_TASK_COMPLETED)
                .isEqualTo("workflow.task.completed");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_TASK_FAILED)
                .isEqualTo("workflow.task.failed");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_TASK_CANCELLED)
                .isEqualTo("workflow.task.cancelled");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_TASK_SUSPENDED)
                .isEqualTo("workflow.task.suspended");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_TASK_RESUMED)
                .isEqualTo("workflow.task.resumed");
        assertThat(StructuredLoggingEventTypes.WORKFLOW_TASK_RETRIED)
                .isEqualTo("workflow.task.retried");
    }

    // Helper method to invoke private shouldLog method via reflection
    private boolean invokeShouldLog(StructuredLoggingListener listener, String eventType) throws Exception {
        Method method = StructuredLoggingListener.class.getDeclaredMethod("shouldLog", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(listener, eventType);
    }
}
