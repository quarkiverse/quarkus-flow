package io.quarkiverse.flow.opentelemetry.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.quarkiverse.flow.dsl.types.CallJava;
import io.serverlessworkflow.api.types.CallA2A;
import io.serverlessworkflow.api.types.CallAsyncAPI;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.CallGRPC;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallMCP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowMutableInstance;
import io.serverlessworkflow.impl.WorkflowMutablePosition;
import io.serverlessworkflow.impl.executors.CallableTask;

@DisplayName("OTelTaskSpanProxy")
class OTelTaskSpanProxyTest {

    private final Tracer tracer = SdkTracerProvider.builder().build().get("test");

    @ParameterizedTest(name = "{0}")
    @MethodSource("javaFunctionCallTasks")
    @DisplayName("should accept Java function call tasks")
    void accept_javaFunctionCallTasks(String name, TaskBase task) {
        assertThat(new OTelTaskSpanProxy(true).accept(task)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("otherCallTasks")
    @DisplayName("should not accept call tasks that may finish their work after apply returns")
    void accept_rejectsOtherCallTasks(String name, TaskBase task) {
        assertThat(new OTelTaskSpanProxy(true).accept(task)).isFalse();
    }

    static Stream<Arguments> javaFunctionCallTasks() {
        return Stream.of(Arguments.of("call Java", new CallFunction().withCall(CallJava.JAVA_CALL_KEY)));
    }

    static Stream<Arguments> otherCallTasks() {
        return Stream.of(
                Arguments.of("call catalog function", new CallFunction().withCall("getPet:1.0.0@default")),
                Arguments.of("call inline function", new CallFunction().withCall("getPet")),
                Arguments.of("no task", null),
                Arguments.of("call http", new CallHTTP()),
                Arguments.of("call openapi", new CallOpenAPI()),
                Arguments.of("call grpc", new CallGRPC()),
                Arguments.of("call a2a", new CallA2A()),
                Arguments.of("call asyncapi", new CallAsyncAPI()),
                Arguments.of("call mcp", new CallMCP()));
    }

    @Test
    @DisplayName("should accept no task when disabled")
    void accept_returnsFalseWhenDisabled() {
        OTelTaskSpanProxy proxy = new OTelTaskSpanProxy(false);
        assertThat(proxy.accept(new CallFunction().withCall(CallJava.JAVA_CALL_KEY))).isFalse();
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
