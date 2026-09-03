package io.quarkiverse.flow.tracing;

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

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import io.quarkiverse.flow.tracing.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;

@DisplayName("TraceLoggerExecutionListener trace correlation")
class TraceLoggerExecutionListenerTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";
    private static final String PARENT_ID = "1100dd0d1d001111";

    private final List<ExtLogRecord> records = new ArrayList<>();
    private Logger julLogger;
    private Handler handler;

    @BeforeEach
    void setUp() {
        MDC.clear();
        records.clear();
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(ExtLogRecord.wrap(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        julLogger = Logger.getLogger(TraceLoggerExecutionListener.class.getName());
        julLogger.addHandler(handler);
        julLogger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        julLogger.removeHandler(handler);
        MDC.clear();
    }

    private static TaskStartedEvent taskStartedEvent() {
        TaskStartedEvent ev = mock(TaskStartedEvent.class, RETURNS_DEEP_STUBS);
        when(ev.workflowContext().instanceData().id()).thenReturn("instance-1");
        when(ev.eventDate()).thenReturn(OffsetDateTime.now());
        when(ev.taskContext().position().jsonPointer()).thenReturn("do/1/greet");
        when(ev.taskContext().taskName()).thenReturn("greet");
        return ev;
    }

    @Test
    @DisplayName("adds trace_id, span_id, sampled and parent_id to the MDC of the emitted log line when a span is active")
    void adds_trace_and_span_id_to_log_line() {
        TraceCorrelationProvider provider = ev -> Optional.of(new TraceContext(TRACE_ID, SPAN_ID, "true", PARENT_ID));

        new TraceLoggerExecutionListener(provider).onTaskStarted(taskStartedEvent());

        assertThat(records).hasSize(1);
        ExtLogRecord record = records.get(0);
        assertThat(record.getMdc("traceId")).isEqualTo(TRACE_ID);
        assertThat(record.getMdc("spanId")).isEqualTo(SPAN_ID);
        assertThat(record.getMdc("sampled")).isEqualTo("true");
        assertThat(record.getMdc("parentId")).isEqualTo(PARENT_ID);
        assertThat(record.getMdc("quarkus.flow.instanceId")).isEqualTo("instance-1");
    }

    @Test
    @DisplayName("does not leak trace correlation keys into the MDC after the log line is emitted")
    void does_not_leak_trace_ids_into_surrounding_mdc() {
        TraceCorrelationProvider provider = ev -> Optional.of(new TraceContext(TRACE_ID, SPAN_ID, "true", PARENT_ID));

        new TraceLoggerExecutionListener(provider).onTaskStarted(taskStartedEvent());

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
        assertThat(MDC.get("sampled")).isNull();
        assertThat(MDC.get("parentId")).isNull();
    }

    @Test
    @DisplayName("emits no trace_id or span_id when the NOOP provider is used")
    void noop_provider_adds_no_trace_ids() {
        new TraceLoggerExecutionListener().onTaskStarted(taskStartedEvent());

        assertThat(records).hasSize(1);
        ExtLogRecord record = records.get(0);
        assertThat(record.getMdc("traceId")).isNull();
        assertThat(record.getMdc("spanId")).isNull();
        assertThat(record.getMdc("quarkus.flow.instanceId")).isEqualTo("instance-1");
    }

    @Test
    @DisplayName("emits no trace correlation keys when the provider reports no active span")
    void empty_provider_result_adds_no_trace_ids() {
        TraceCorrelationProvider provider = ev -> Optional.empty();

        new TraceLoggerExecutionListener(provider).onTaskStarted(taskStartedEvent());

        assertThat(records).hasSize(1);
        ExtLogRecord record = records.get(0);
        assertThat(record.getMdc("traceId")).isNull();
        assertThat(record.getMdc("spanId")).isNull();
    }
}