package io.quarkiverse.flow.opentelemetry.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider.TraceContext;
import io.serverlessworkflow.api.types.SetTask;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowPosition;
import io.serverlessworkflow.impl.lifecycle.TaskStartedEvent;
import io.serverlessworkflow.impl.lifecycle.WorkflowStartedEvent;

@DisplayName("OTelTraceCorrelationProvider reads the captured trace identifiers")
class OTelTraceCorrelationProviderTest {

    private static final String WF_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String WF_SPAN_ID = "b7ad6b7169203331";
    private static final String TASK_SPAN_ID = "00f067aa0ba902b7";
    private static final String TASK_ID = "do/0/set-0";

    private static final TraceContext WF_CONTEXT = new TraceContext(WF_TRACE_ID, WF_SPAN_ID, "true", "");
    private static final TraceContext TASK_CONTEXT = new TraceContext(WF_TRACE_ID, TASK_SPAN_ID, "true", WF_SPAN_ID);

    private final OTelTraceCorrelationProvider provider = new OTelTraceCorrelationProvider();

    private static WorkflowInstrumentationContext newContext() {
        return new WorkflowInstrumentationContext(
                InstrumentationContext.newBuilder().withStartTime(Instant.now()).build());
    }

    private static WorkflowStartedEvent workflowEventWith(WorkflowInstrumentationContext ctx) {
        WorkflowStartedEvent ev = mock(WorkflowStartedEvent.class, RETURNS_DEEP_STUBS);
        WorkflowInstanceData instanceData = ev.workflowContext().instanceData();
        when(instanceData.findMetadata(anyString(), eq(WorkflowInstrumentationContext.class)))
                .thenReturn(Optional.ofNullable(ctx));
        return ev;
    }

    private static TaskStartedEvent taskEventWith(WorkflowInstrumentationContext ctx) {
        TaskStartedEvent ev = mock(TaskStartedEvent.class, RETURNS_DEEP_STUBS);
        WorkflowInstanceData instanceData = ev.workflowContext().instanceData();
        when(instanceData.findMetadata(anyString(), eq(WorkflowInstrumentationContext.class)))
                .thenReturn(Optional.ofNullable(ctx));

        TaskContext taskContext = mock(TaskContext.class);
        WorkflowPosition position = mock(WorkflowPosition.class);
        when(position.jsonPointer()).thenReturn(TASK_ID);
        when(taskContext.position()).thenReturn(position);
        when(taskContext.taskName()).thenReturn("set-0");
        when(taskContext.task()).thenReturn(new SetTask());
        when(taskContext.iteration()).thenReturn(0);
        when(taskContext.isRetrying()).thenReturn(false);
        when(taskContext.retryAttempt()).thenReturn(0);
        when(taskContext.tryRetryCount()).thenReturn(Optional.empty());
        when(ev.taskContext()).thenReturn(taskContext);
        return ev;
    }

    @Test
    @DisplayName("a workflow event returns the captured workflow-instance trace context")
    void workflow_event_returns_workflow_context() {
        WorkflowInstrumentationContext ctx = newContext();
        ctx.setWorkflowTraceContext(WF_CONTEXT);

        assertThat(provider.traceContextFor(workflowEventWith(ctx))).contains(WF_CONTEXT);
    }

    @Test
    @DisplayName("returns empty when the instance has no instrumentation context")
    void no_instrumentation_context_returns_empty() {
        assertThat(provider.traceContextFor(workflowEventWith(null))).isEmpty();
    }

    @Test
    @DisplayName("returns empty when no span has been captured yet")
    void nothing_captured_returns_empty() {
        assertThat(provider.traceContextFor(workflowEventWith(newContext()))).isEmpty();
    }

    @Test
    @DisplayName("a task event returns the captured task trace context")
    void task_event_returns_task_context() {
        WorkflowInstrumentationContext ctx = newContext();
        ctx.setWorkflowTraceContext(WF_CONTEXT);
        ctx.putTaskTraceContext(TASK_ID, 0, 0, TASK_CONTEXT);

        assertThat(provider.traceContextFor(taskEventWith(ctx))).contains(TASK_CONTEXT);
    }

    @Test
    @DisplayName("a terminal task event still resolves the task's own span after the active context is removed")
    void terminal_task_event_still_resolves_task_context() {
        WorkflowInstrumentationContext ctx = newContext();
        ctx.setWorkflowTraceContext(WF_CONTEXT);
        ctx.putTaskTraceContext(TASK_ID, 0, 0, TASK_CONTEXT);
        // the OTel listener ends the span and drops the active context on a terminal event;
        // the captured trace identifiers are independent of that map
        ctx.removeTaskInstanceInstanceContext(TASK_ID, 0, 0);

        assertThat(provider.traceContextFor(taskEventWith(ctx))).contains(TASK_CONTEXT);
    }

    @Test
    @DisplayName("a task event with no captured task span falls back to the workflow trace context")
    void task_event_falls_back_to_workflow_context() {
        WorkflowInstrumentationContext ctx = newContext();
        ctx.setWorkflowTraceContext(WF_CONTEXT);

        assertThat(provider.traceContextFor(taskEventWith(ctx))).contains(WF_CONTEXT);
    }

    @Test
    @DisplayName("a task event with neither task nor workflow context returns empty")
    void task_event_without_any_context_returns_empty() {
        assertThat(provider.traceContextFor(taskEventWith(newContext()))).isEmpty();
    }
}