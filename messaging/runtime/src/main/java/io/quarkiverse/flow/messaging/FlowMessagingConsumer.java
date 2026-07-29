package io.quarkiverse.flow.messaging;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
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
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.serverlessworkflow.impl.events.AbstractTypeConsumer;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class FlowMessagingConsumer
        extends AbstractTypeConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FlowMessagingConsumer.class);
    private final Map<String, Consumer<CloudEvent>> topicMap = new ConcurrentHashMap<>();
    private final AtomicReference<Consumer<CloudEvent>> allConsumerRef = new AtomicReference<>();

    @Inject
    ManagedExecutor executor;

    private static CloudEvent resolveCloudEvent(Message<?> msg) {
        IncomingCloudEventMetadata<?> meta = (IncomingCloudEventMetadata<?>) msg
                .getMetadata(IncomingCloudEventMetadata.class)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Message does not carry CloudEvent metadata. "
                                + "Ensure the channel has cloud-events enabled (the default)."));

        CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(meta.getId())
                .withSource(meta.getSource())
                .withType(meta.getType());

        meta.getDataContentType().ifPresent(builder::withDataContentType);
        meta.getDataSchema().ifPresent(builder::withDataSchema);
        meta.getSubject().ifPresent(builder::withSubject);
        meta.getTimeStamp()
                .map(ZonedDateTime::toOffsetDateTime)
                .ifPresent(builder::withTime);

        for (Map.Entry<String, Object> ext : meta.getExtensions().entrySet()) {
            Object val = ext.getValue();
            if (val instanceof String s) {
                builder.withExtension(ext.getKey(), s);
            } else if (val instanceof Number n) {
                builder.withExtension(ext.getKey(), n);
            } else if (val instanceof Boolean b) {
                builder.withExtension(ext.getKey(), b);
            } else if (val != null) {
                builder.withExtension(ext.getKey(), val.toString());
            }
        }

        Object data = meta.getData();
        if (data instanceof byte[] bytes) {
            builder.withData(bytes);
        } else if (data instanceof CloudEventData cloudEventData) {
            builder.withData(cloudEventData);
        } else if (data instanceof String text) {
            builder.withData(text.getBytes(StandardCharsets.UTF_8));
        } else if (data instanceof JsonObject jsonObject) {
            builder.withData(jsonObject.encode().getBytes(StandardCharsets.UTF_8));
        } else if (data != null) {
            builder.withData(BytesCloudEventData.wrap(data.toString().getBytes(StandardCharsets.UTF_8)));
        }

        return builder.build();
    }

    @Incoming("flow-in")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<Void> onIncoming(Message<?> msg) {
        final CloudEvent ce;
        try {
            ce = resolveCloudEvent(msg);
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
