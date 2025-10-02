package io.quarkiverse.flow.messaging;

import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;

import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;

public class FlowDomainEventsPublisher extends ContentBasedRouterEventsPublisher {

    private static final String CHANNEL_NAME = "flow-out";

    @Inject
    @Channel(CHANNEL_NAME)
    MutinyEmitter<byte[]> out;

    @Override
    MutinyEmitter<byte[]> outEmitter() {
        return out;
    }

    @Override
    boolean accept(CloudEvent event) {
        final String type = event.getType();
        return type != null && !type.startsWith(ENGINE_PREFIX);
    }

    @Override
    String channelName() {
        return CHANNEL_NAME;
    }
}
