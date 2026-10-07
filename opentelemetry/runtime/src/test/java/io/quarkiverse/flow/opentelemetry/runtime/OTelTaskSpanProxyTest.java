package io.quarkiverse.flow.opentelemetry.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutableInstance;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.executors.CallableTask;

@DisplayName("OTelTaskSpanProxy")
class OTelTaskSpanProxyTest {

    private final Tracer tracer = SdkTracerProvider.builder().build().get("test");

    @Test
    @DisplayName("should accept all tasks")
    void accept_returnsTrue() {
        OTelTaskSpanProxy proxy = new OTelTaskSpanProxy(true);
        assertThat(proxy.accept(null)).isTrue();
    }

    @Test
    @DisplayName("should accept no task when disabled")
    void accept_returnsFalseWhenDisabled() {
        OTelTaskSpanProxy proxy = new OTelTaskSpanProxy(false);
        assertThat(proxy.accept(null)).isFalse();
    }

    @Test
    @DisplayName("should run the delegate unchanged when the workflow has no instrumentation context")
    void build_runsDelegateWithoutInstrumentationContext() throws Exception {
        WorkflowMutableInstance instanceData = mock(WorkflowMutableInstance.class);
        when(instanceData.findMetadata("OTEL_CONTEXT", WorkflowInstrumentationContext.class))
                .thenReturn(java.util.Optional.empty());
        WorkflowContext workflowContext = mock(WorkflowContext.class);
        when(workflowContext.instanceData()).thenReturn(instanceData);
        WorkflowModel output = mock(WorkflowModel.class);

        CallableTask wrapped = new OTelTaskSpanProxy(true).build((wf, task, input) -> {
            assertThat(Span.current().getSpanContext().isValid()).isFalse();
            return CompletableFuture.completedFuture(output);
        });

        assertThat(wrapped.apply(workflowContext, mock(TaskContext.class), mock(WorkflowModel.class)).get())
                .isSameAs(output);
    }

    @Test
    @DisplayName("should have priority 100")
    void priority_is100() {
        OTelTaskSpanProxy proxy = new OTelTaskSpanProxy(true);
        assertThat(proxy.priority()).isEqualTo(100);
    }

    @Test
    @DisplayName("should make task span current while executing task delegate")
    void build_makesTaskSpanCurrentInDelegate() throws Exception {
        OTelTaskSpanProxy proxy = new OTelTaskSpanProxy(true);

        Span expectedSpan = tracer.spanBuilder("test-task-span").startSpan();

        InstrumentationContext taskCtx = mock(InstrumentationContext.class);
        when(taskCtx.getStartSpan()).thenReturn(expectedSpan);
        when(taskCtx.getParentContext()).thenReturn(Context.root());

        WorkflowInstrumentationContext wfCtx = mock(WorkflowInstrumentationContext.class);
        when(wfCtx.getTaskInstanceContext(any(), any(Integer.class), any(Integer.class))).thenReturn(taskCtx);

        WorkflowMutableInstance instanceData = mock(WorkflowMutableInstance.class);
        when(instanceData.findMetadata("OTEL_CONTEXT", WorkflowInstrumentationContext.class))
                .thenReturn(java.util.Optional.of(wfCtx));
        WorkflowContext workflowContext = mock(WorkflowContext.class);
        when(workflowContext.instanceData()).thenReturn(instanceData);

        try {
            CallableTask delegate = (wf, task, input) -> {
                Span activeSpan = Span.current();
                assertThat(activeSpan.getSpanContext().getSpanId())
                        .isEqualTo(expectedSpan.getSpanContext().getSpanId());
                return CompletableFuture.completedFuture(mock(WorkflowModel.class));
            };

            CallableTask wrapped = proxy.build(delegate);

            TaskContext taskContext = mock(TaskContext.class);
            WorkflowMutablePosition position = mock(WorkflowMutablePosition.class);
            when(position.jsonPointer()).thenReturn("/do/0/test");
            when(taskContext.position()).thenReturn(position);
            when(taskContext.iteration()).thenReturn(0);
            when(taskContext.retryAttempt()).thenReturn(0);

            wrapped.apply(workflowContext, taskContext, mock(WorkflowModel.class)).get();

            assertThat(Span.current().getSpanContext().isValid())
                    .as("the task span is no longer current once the task body returns")
                    .isFalse();
        } finally {
            expectedSpan.end();
        }
    }
}
