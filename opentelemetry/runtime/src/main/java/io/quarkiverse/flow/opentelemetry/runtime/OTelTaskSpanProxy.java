package io.quarkiverse.flow.opentelemetry.runtime;

import org.eclipse.microprofile.config.ConfigProvider;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkiverse.flow.dsl.types.CallJava;
import io.quarkiverse.flow.opentelemetry.runtime.config.FlowOTelConfig;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.executors.CallableTask;
import io.serverlessworkflow.impl.executors.CallableTaskProxyBuilder;

/**
 * Makes a call task's {@code task.execute} span current while the task body runs.
 * <p>
 * {@link OTelWorkflowExecutionListener} starts the task span in {@code onTaskStarted} but only records it in the
 * {@link WorkflowInstrumentationContext}. Without this proxy, {@code Span.current()} inside the task body is whatever
 * was current on the executing thread (usually nothing), so spans created by the body start new traces.
 * <p>
 * Controlled by {@code quarkus.flow.otel.task-span-current} (default {@code true}).
 * <p>
 * The scope only covers {@link CallableTask#apply}, so it is only applied to Java function call tasks
 * ({@code call: Java}, which is what the Java DSL's {@code function(..)}, {@code consume(..)} and {@code agent(..)}
 * produce, including the tasks of generated agentic workflows). Their body either runs inside {@code apply} or is
 * submitted to the workflow's managed executor from inside it, which carries the current context over.
 * <p>
 * Other call tasks keep the previous behavior. HTTP, OpenAPI, gRPC and similar tasks don't do their work inside
 * {@code apply}: they finish it asynchronously after {@code apply} returns. Making the task span current around
 * {@code apply} doesn't reliably reach their client spans, and in testing it sometimes attached a call to the span of a
 * different task. A {@code call: <function>} referring to a catalog or inline function can resolve to any of those, so
 * it is excluded too.
 */
public class OTelTaskSpanProxy implements CallableTaskProxyBuilder {

    /**
     * Lower than the fault tolerance proxy's default priority: proxies are applied in ascending priority order, so this
     * one wraps the task body directly and the fault tolerance proxy wraps it, making the span current on every retry.
     */
    static final int PRIORITY = 100;

    private final boolean enabled;

    public OTelTaskSpanProxy() {
        this(ConfigProvider.getConfig()
                .getOptionalValue(FlowOTelConfig.QUARKUS_FLOW_OTEL_TASK_SPAN_CURRENT, Boolean.class)
                .orElse(true));
    }

    OTelTaskSpanProxy(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean accept(TaskBase taskBase) {
        return enabled && taskBase instanceof CallFunction function && CallJava.JAVA_CALL_KEY.equals(function.getCall());
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public CallableTask build(CallableTask delegate) {
        return (workflowContext, taskContext, input) -> {
            WorkflowInstrumentationContext ctx = WorkflowInstrumentationContext
                    .getWorkflowInstrumentationContext(workflowContext.instanceData());
            if (ctx == null) {
                return delegate.apply(workflowContext, taskContext, input);
            }

            InstrumentationContext taskSpanCtx = ctx.getTaskInstanceContext(
                    taskContext.position().jsonPointer(),
                    taskContext.iteration(),
                    taskContext.retryAttempt());

            if (taskSpanCtx == null || taskSpanCtx.getStartSpan() == null) {
                return delegate.apply(workflowContext, taskContext, input);
            }

            Context taskSpanContext = taskSpanCtx.getStartSpan().storeInContext(taskSpanCtx.getParentContext());
            try (Scope ignored = taskSpanContext.makeCurrent()) {
                return delegate.apply(workflowContext, taskContext, input);
            }
        };
    }
}
