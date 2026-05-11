package io.quarkiverse.flow.opentelemetry.runtime;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.events.EmittedEventDecorator;

public class OTelEmittedEventDecorator implements EmittedEventDecorator {

    @Override
    public void decorate(CloudEventBuilder decorated, WorkflowContext workflowContext, TaskContext taskContext) {
        CDIOTelEmittedEventDecorator delegate = CDIDecoratorProvider.getEventDecorator();
        if (delegate == null) {
            throw new IllegalStateException(CDIOTelEmittedEventDecorator.class.getName() + " bean could not be found.");
        }
        delegate.decorate(decorated, workflowContext, taskContext);
    }
}
