package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.core.builder.CloudEventBuilder;
import io.quarkus.arc.Arc;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.events.EmittedEventDecorator;

public class OTelEmittedEventDecorator implements EmittedEventDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OTelEmittedEventDecorator.class);
    private static volatile CDIOTelEmittedEventDecorator DELEGATE;
    private static final ReentrantLock LOCK = new ReentrantLock();

    @Override
    public void decorate(CloudEventBuilder decorated, WorkflowContext workflowContext, TaskContext taskContext) {
        CDIOTelEmittedEventDecorator delegate = DELEGATE;
        if (delegate == null) {
            LOCK.lock();
            try {
                delegate = DELEGATE;
                if (delegate == null) {
                    LOGGER.debug("Instantiating the " + CDIOTelEmittedEventDecorator.class.getName());
                    delegate = Arc.container()
                            .instance(CDIOTelEmittedEventDecorator.class)
                            .get();

                    if (delegate == null) {
                        throw new IllegalStateException(
                                CDIOTelEmittedEventDecorator.class.getName() + " bean could not be found.");
                    }
                    DELEGATE = delegate;
                }
            } finally {
                LOCK.unlock();
            }
        }
        delegate.decorate(decorated, workflowContext, taskContext);
    }
}
