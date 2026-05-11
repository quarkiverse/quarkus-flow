package io.quarkiverse.flow.opentelemetry.runtime;

import static io.quarkiverse.flow.opentelemetry.runtime.OTelWorkflowExecutionListener.printThreadAndCurrentVertxContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.quarkiverse.flow.opentelemetry.runtime.config.FlowOTelConfig;
import io.quarkus.runtime.Startup;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.events.EmittedEventDecorator;

@ApplicationScoped
@Startup
public class CDIOTelEmittedEventDecorator implements EmittedEventDecorator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CDIOTelEmittedEventDecorator.class);
    @Inject
    InstrumentationContextManager contextManager;
    @Inject
    FlowOTelConfig oTelConfig;

    @Override
    public void decorate(CloudEventBuilder builder, WorkflowContext workflowContext, TaskContext taskContext) {
        if (!oTelConfig.isEnabled()) {
            return;
        }
        String workflowInstanceId = workflowContext.instanceData().id();
        String taskId = taskContext.position().jsonPointer();
        int iteration = taskContext.iteration();
        int retryAttempt = taskContext.retryAttempt();

        LOGGER.debug("Decorating cloud event for workflowInstanceId: " + workflowInstanceId + ", taskId: " + taskId
                + ", iteration: " + iteration + ", retryAttempt: " + retryAttempt);

        InstrumentationContext taskInstanceContext = contextManager.getTaskInstanceContext(workflowInstanceId, taskId,
                iteration, retryAttempt);

        if (taskInstanceContext == null) {
            LOGGER.warn("No taskInstanceContext was found for workflowInstanceId: " + workflowInstanceId
                    + ", taskId: " + taskId + ", iteration: " + iteration + ", retryAttempt: " + retryAttempt);
            return;
        }

        TextMapSetter<CloudEventBuilder> setter = (carrier, name, value) -> {
            if (carrier != null) {
                LOGGER.debug("Setting cloud event context attribute name: " + name + " with value: " + value);
                carrier.withContextAttribute(name, value);
            }
        };

        printThreadAndCurrentVertxContext("DECORATING EVENT for - taskId: " + taskId);

        Context propagtedContext = taskInstanceContext.getStartSpan().storeInContext(taskInstanceContext.getParentContext());
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(
                propagtedContext,
                builder,
                setter);
    }
}
