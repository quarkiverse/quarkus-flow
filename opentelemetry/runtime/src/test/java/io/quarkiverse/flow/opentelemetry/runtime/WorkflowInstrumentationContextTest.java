package io.quarkiverse.flow.opentelemetry.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider.TraceContext;

@DisplayName("WorkflowInstrumentationContext trace-correlation store")
class WorkflowInstrumentationContextTest {

    private static final int TRACE_CONTEXT_MAX = 256;

    private static WorkflowInstrumentationContext newContext() {
        return new WorkflowInstrumentationContext(
                InstrumentationContext.newBuilder().withStartTime(Instant.now()).build());
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
}