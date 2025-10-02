package io.quarkiverse.flow.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;

import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;

// TODO: in the engine, create a specialized EventPublisher for lifecycle events

@ApplicationScoped
public class FlowLifecycleEventsPublisher extends ContentBasedRouterEventsPublisher {

    private static final String CHANNEL_NAME = "flow-lifecycle-out";

    @Inject
    @Channel(CHANNEL_NAME) // <— new channel name
    MutinyEmitter<byte[]> out;

    @Override
    protected MutinyEmitter<byte[]> outEmitter() {
        return out;
    }

    @Override
    protected boolean accept(CloudEvent event) {
        return isLifecycleEvent(event);
    }

    @Override
    protected String channelName() {
        return CHANNEL_NAME;
    }
}
