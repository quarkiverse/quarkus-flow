package io.quarkiverse.flow.messaging;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.serverlessworkflow.impl.events.AbstractTypeConsumer;

@ApplicationScoped
public class FlowMessagingConsumer
        extends AbstractTypeConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FlowMessagingConsumer.class);
    private static final JsonFormat FORMAT = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);
    private final Map<String, Consumer<CloudEvent>> topicMap = new ConcurrentHashMap<>();
    private final AtomicReference<Consumer<CloudEvent>> allConsumerRef = new AtomicReference<>();
    @Inject
    ManagedExecutor executor;

    private static CloudEvent parseStructuredCE(byte[] json) {
        if (FORMAT == null)
            throw new IllegalStateException("CloudEvents JSON format not available");
        return FORMAT.deserialize(json);
    }

    @Incoming("flow-in")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<Void> onIncoming(Message<byte[]> msg) {
        final CloudEvent ce;
        try {
            ce = parseStructuredCE(msg.getPayload());
            LOG.debug("Flow: Received event: {}", ce);
        } catch (Exception e) {
            LOG.error("Flow: CE parse failure", e);
            return msg.nack(e);
        }

        // Handoff to worker thread; DO NOT block the event loop.
        // This way we isolate the runtime workflow engine process from the Vert.x executor thread, avoiding blocking exceptions
        return executor
                .runAsync(() -> dispatch(ce)) // dispatch() does routing & triggers workflow, but returns quickly
                .thenCompose(v -> msg.ack()) // single ack after successful handoff
                .exceptionally(ex -> {
                    LOG.error("Flow: Failed to handoff event", ex);
                    msg.nack(ex);
                    return null;
                });
    }

    private void dispatch(CloudEvent ce) {
        // Generic fan-out: no blocking calls here; just schedule/offer to workflow engine
        final Consumer<CloudEvent> all = allConsumerRef.get();
        if (all != null)
            all.accept(ce);

        final Consumer<CloudEvent> c = topicMap.get(ce.getType());
        if (c != null)
            c.accept(ce);
    }

    @Override
    protected void registerToAll(Consumer<CloudEvent> consumer) {
        allConsumerRef.set(consumer);
    }

    @Override
    protected void unregisterFromAll() {
        allConsumerRef.set(null);
    }

    @Override
    protected void register(String type, Consumer<CloudEvent> consumer) {
        topicMap.put(type, consumer);
    }

    @Override
    protected void unregister(String type) {
        topicMap.remove(type);
    }

    @Override
    public void close() {
        topicMap.clear();
        allConsumerRef.set(null);
    }
}
