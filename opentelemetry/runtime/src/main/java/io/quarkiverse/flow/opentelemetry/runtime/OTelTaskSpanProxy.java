package io.quarkiverse.flow.opentelemetry.runtime;

import org.eclipse.microprofile.config.ConfigProvider;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkiverse.flow.opentelemetry.runtime.config.FlowOTelConfig;
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
        return enabled;
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
