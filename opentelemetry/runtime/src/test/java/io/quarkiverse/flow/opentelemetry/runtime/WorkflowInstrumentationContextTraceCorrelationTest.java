package io.quarkiverse.flow.opentelemetry.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.tracing.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.impl.WorkflowInstanceData;

@DisplayName("WorkflowInstrumentationContext trace-correlation store")
class WorkflowInstrumentationContextTraceCorrelationTest {

    private static final int TRACE_CONTEXT_MAX = 256;

    private static WorkflowInstrumentationContext newContext() {
        return newContext(1);
    }

    private static WorkflowInstrumentationContext newContext(int expectedConsumers) {
        return new WorkflowInstrumentationContext(
                mock(WorkflowInstanceData.class),
                InstrumentationContext.newBuilder().withStartTime(Instant.now()).build(),
                expectedConsumers);
    }

    private static TraceContext trace(String spanId) {
        return new TraceContext("0af7651916cd43dd8448eb211c80319c", spanId, "true", "");
    }

    @Test
    @DisplayName("a captured task trace context survives removal of the active span context")
    void task_trace_context_survives_active_context_removal() {
        WorkflowInstrumentationContext ctx = newContext();
        ctx.putTaskTraceContext("do/0/a", 0, 0, trace("aaaaaaaaaaaaaaaa"));

        ctx.removeTaskInstanceInstanceContext("do/0/a", 0, 0);

        assertThat(ctx.getTaskTraceContext("do/0/a", 0, 0)).isEqualTo(trace("aaaaaaaaaaaaaaaa"));
    }

    @Test
    @DisplayName("a null task trace context is ignored")
    void null_task_trace_context_is_ignored() {
        WorkflowInstrumentationContext ctx = newContext();

        ctx.putTaskTraceContext("do/0/a", 0, 0, null);

        assertThat(ctx.getTaskTraceContext("do/0/a", 0, 0)).isNull();
    }

    @Test
    @DisplayName("the task trace store is bounded and evicts the eldest entries")
    void task_trace_store_is_bounded() {
        WorkflowInstrumentationContext ctx = newContext();
        for (int i = 0; i < TRACE_CONTEXT_MAX + 50; i++) {
            ctx.putTaskTraceContext("do/0/task", i, 0, trace(String.format("%016x", i)));
        }

        // the very first entries have been evicted
        assertThat(ctx.getTaskTraceContext("do/0/task", 0, 0)).isNull();
        // the most recent ones are retained
        assertThat(ctx.getTaskTraceContext("do/0/task", TRACE_CONTEXT_MAX + 49, 0)).isNotNull();
    }

    @Test
    @DisplayName("closing the context clears the captured task trace store")
    void close_clears_task_trace_store() throws Exception {
        WorkflowInstrumentationContext ctx = newContext();
        ctx.putTaskTraceContext("do/0/a", 0, 0, trace("aaaaaaaaaaaaaaaa"));

        ctx.close();

        assertThat(ctx.getTaskTraceContext("do/0/a", 0, 0)).isNull();
    }

    @Test
    @DisplayName("a terminal read is kept until every registered consumer has read it")
    void terminal_read_survives_until_every_consumer_has_read_it() {
        WorkflowInstrumentationContext ctx = newContext(2);
        ctx.putTaskTraceContext("do/0/a", 0, 0, trace("aaaaaaaaaaaaaaaa"));

        // first of two expected consumers reads it: still there for the second one
        assertThat(ctx.consumeTerminalTaskTraceContext("do/0/a", 0, 0)).isEqualTo(trace("aaaaaaaaaaaaaaaa"));
        assertThat(ctx.getTaskTraceContext("do/0/a", 0, 0)).isNotNull();

        // second (and last) expected consumer reads it: now released
        assertThat(ctx.consumeTerminalTaskTraceContext("do/0/a", 0, 0)).isEqualTo(trace("aaaaaaaaaaaaaaaa"));
        assertThat(ctx.getTaskTraceContext("do/0/a", 0, 0)).isNull();
    }

    @Test
    @DisplayName("non-terminal reads (e.g. suspend/resume) do not count toward the terminal release")
    void non_terminal_reads_do_not_count_toward_release() {
        WorkflowInstrumentationContext ctx = newContext(1);
        ctx.putTaskTraceContext("do/0/a", 0, 0, trace("aaaaaaaaaaaaaaaa"));

        // a task suspended/resumed several times reads the same entry repeatedly
        ctx.getTaskTraceContext("do/0/a", 0, 0);
        ctx.getTaskTraceContext("do/0/a", 0, 0);
        ctx.getTaskTraceContext("do/0/a", 0, 0);

        // the single expected consumer still gets it on the terminal event, and only then is it released
        assertThat(ctx.consumeTerminalTaskTraceContext("do/0/a", 0, 0)).isEqualTo(trace("aaaaaaaaaaaaaaaa"));
        assertThat(ctx.getTaskTraceContext("do/0/a", 0, 0)).isNull();
    }

    @Test
    @DisplayName("a terminal read on a missing entry returns null without error")
    void terminal_read_on_missing_entry_returns_null() {
        WorkflowInstrumentationContext ctx = newContext();

        assertThat(ctx.consumeTerminalTaskTraceContext("do/0/missing", 0, 0)).isNull();
    }
}
