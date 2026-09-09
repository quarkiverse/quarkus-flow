package io.quarkiverse.flow.structuredlogging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.config.FlowStructuredLoggingConfig;
import io.quarkiverse.flow.config.TimestampFormat;
import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider;
import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

@DisplayName("StructuredLoggingListener trace correlation")
class StructuredLoggingListenerTraceCorrelationTest {

    private static final String LOG_CATEGORY = "io.quarkiverse.flow.structuredlogging";
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    private final List<LogRecord> records = new ArrayList<>();
    private Logger julLogger;
    private Handler handler;
    private FlowStructuredLoggingConfig config;

    @BeforeEach
    void setUp() {
        records.clear();
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        julLogger = Logger.getLogger(LOG_CATEGORY);
        julLogger.addHandler(handler);
        julLogger.setLevel(Level.ALL);

        config = mock(FlowStructuredLoggingConfig.class);
        when(config.enabled()).thenReturn(true);
        when(config.events()).thenReturn(List.of("*"));
        when(config.logLevel()).thenReturn("INFO");
        when(config.timestampFormat()).thenReturn(TimestampFormat.ISO8601);
        when(config.includeWorkflowPayloads()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        julLogger.removeHandler(handler);
    }

    private static WorkflowStartedEvent workflowStartedEvent() {
        WorkflowStartedEvent ev = mock(WorkflowStartedEvent.class, RETURNS_DEEP_STUBS);
        when(ev.eventDate()).thenReturn(OffsetDateTime.now());
        when(ev.workflowContext().instanceData().id()).thenReturn("instance-1");
        return ev;
    }

    @SuppressWarnings("unchecked")
    private static Instance<TraceCorrelationProvider> instanceOf(TraceCorrelationProvider provider) {
        Instance<TraceCorrelationProvider> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(provider != null);
        when(instance.get()).thenReturn(provider);
        return instance;
    }

    @Test
    @DisplayName("emitted JSON carries traceId/spanId when a provider resolves a span")
    void emits_trace_fields() {
        TraceCorrelationProvider provider = ev -> Optional.of(new TraceContext(TRACE_ID, SPAN_ID, "true", ""));
        StructuredLoggingListener listener = new StructuredLoggingListener(config, new ObjectMapper(),
                instanceOf(provider));

        listener.onWorkflowStarted(workflowStartedEvent());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getMessage())
                .contains("\"traceId\":\"" + TRACE_ID + "\"")
                .contains("\"spanId\":\"" + SPAN_ID + "\"");
    }

    @Test
    @DisplayName("emitted JSON has no trace fields when no provider is resolvable")
    void no_provider_no_trace_fields() {
        StructuredLoggingListener listener = new StructuredLoggingListener(config, new ObjectMapper(),
                instanceOf(null));

        listener.onWorkflowStarted(workflowStartedEvent());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getMessage()).doesNotContain("traceId");
    }
}