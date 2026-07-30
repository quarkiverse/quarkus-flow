package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.events.EmittedEventDecorator;

public class OTelEmittedEventDecorator implements EmittedEventDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OTelEmittedEventDecorator.class);
    private static final AtomicReference<Boolean> ENABLED = new AtomicReference<>();

    @Override
    public void decorate(CloudEventBuilder decorated, WorkflowContext workflowContext, TaskContext taskContext) {
        if (!isOtelEnabled()) {
            return;
        }
        String workflowInstanceId = workflowContext.instanceData().id();
        String taskId = taskContext.position().jsonPointer();
        int iteration = taskContext.iteration();
        int retryAttempt = taskContext.retryAttempt();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Decorating cloud event for workflowInstanceId: {}, taskId: {}, iteration: {}, retryAttempt: {}",
                    workflowInstanceId, taskId, iteration, retryAttempt);
        }

        WorkflowInstrumentationContext workflowInstrumentationContext = WorkflowInstrumentationContext
                .getWorkflowInstrumentationContext(workflowContext.instanceData());
        if (workflowInstrumentationContext == null) {
            LOGGER.warn(
                    "No instrumentation context was found for workflowApplicationId: {}, workflowNamespace: {}, workflowName: {}, workflowInstanceId: {}, workflowVersion: {}",
                    workflowContext.definition().application().id(), workflowContext.definition().id().namespace(),
                    workflowContext.definition().id().name(), workflowInstanceId, workflowContext.definition().id().version());
            return;
        }

        InstrumentationContext taskInstanceContext = workflowInstrumentationContext.getTaskInstanceContext(taskId,
                iteration, retryAttempt);

        if (taskInstanceContext == null) {
            LOGGER.warn(
                    "No taskInstanceContext was found for workflowInstanceId: {}, taskId: {}, iteration: {}, retryAttempt: {}",
                    workflowInstanceId, taskId, iteration, retryAttempt);
            return;
        }

        TextMapSetter<CloudEventBuilder> setter = (carrier, name, value) -> {
            if (carrier != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Setting cloud event context attribute name: {}, with value: {}", name, value);
                }
                carrier.withContextAttribute(name, value);
            }
        };

        Context propagtedContext = taskInstanceContext.getStartSpan().storeInContext(taskInstanceContext.getParentContext());
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(
                propagtedContext,
                decorated,
                setter);
    }

    private static boolean isOtelEnabled() {
        if (ENABLED.get() == null) {
            boolean enabled = ConfigProvider.getConfig().getOptionalValue("quarkus.flow.otel.enabled", Boolean.class)
                    .orElse(true);
            ENABLED.set(enabled);
        }
        return ENABLED.get();
    }
}
