package io.quarkiverse.flow.messaging;

import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.smallrye.reactive.messaging.MutinyEmitter;

@ApplicationScoped
public class FlowLifecycleEventsPublisher implements EventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(FlowLifecycleEventsPublisher.class);
    private static final String ENGINE_PREFIX = "io.serverlessworkflow";
    private static final JsonFormat FORMAT = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    @Channel("flow-lifecycle-out") // <— new channel name
    MutinyEmitter<byte[]> out;

    // TODO: in the engine, create a specialized EventPublisher for lifecycle events

    @Override
    public CompletableFuture<Void> publish(CloudEvent event) {
        final String type = event.getType();
        if (type == null || !type.startsWith(ENGINE_PREFIX)) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            byte[] structured = FORMAT.serialize(event);
            LOG.debug("Flow: Lifecycle publisher -> flow-lifecycle-out, event={}", new String(structured));
            return out.sendMessage(Message.of(structured)).subscribeAsCompletionStage();
        } catch (Throwable t) {
            final CompletableFuture<Void> cf = new CompletableFuture<>();
            cf.completeExceptionally(t);
            return cf;
        }
    }

    @Override
    public void close() {
        //no-op
    }
}
