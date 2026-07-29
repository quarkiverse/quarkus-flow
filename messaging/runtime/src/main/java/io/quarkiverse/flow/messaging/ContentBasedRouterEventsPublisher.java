package io.quarkiverse.flow.messaging;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;

/**
 * A content-based router {@link EventPublisher}.
 * <p>
 * CloudEvent attributes are propagated via {@link OutgoingCloudEventMetadata},
 * letting SmallRye Reactive Messaging handle transport-specific encoding
 * (Kafka headers, AMQP properties, etc.) rather than manually serializing
 * the full CloudEvent envelope.
 *
 * @see <a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/ContentBasedRouter.html">Messaging Patterns -
 *      Content-Based Router</a>
 */
public abstract class ContentBasedRouterEventsPublisher implements EventPublisher {

    private static final String ENGINE_PREFIX = "io.serverlessworkflow";
    private static final Logger LOG = LoggerFactory.getLogger(ContentBasedRouterEventsPublisher.class);

    protected boolean isLifecycleEvent(CloudEvent event) {
        final String type = event.getType();
        return type != null && type.startsWith(ENGINE_PREFIX);
    }

    @Override
    public final CompletableFuture<Void> publish(CloudEvent event) {
        if (!accept(event))
            return CompletableFuture.completedFuture(null);

        try {
            byte[] data = event.getData() != null ? event.getData().toBytes() : new byte[0];

            var builder = OutgoingCloudEventMetadata.builder()
                    .withId(event.getId())
                    .withSource(event.getSource())
                    .withType(event.getType());

            if (event.getDataContentType() != null) {
                builder.withDataContentType(event.getDataContentType());
            }
            if (event.getDataSchema() != null) {
                builder.withDataSchema(event.getDataSchema());
            }
            if (event.getSubject() != null) {
                builder.withSubject(event.getSubject());
            }
            if (event.getTime() != null) {
                builder.withTimestamp(event.getTime().atZoneSameInstant(ZoneOffset.UTC));
            }

            if (!event.getExtensionNames().isEmpty()) {
                Map<String, Object> extensions = new HashMap<>();
                for (String name : event.getExtensionNames()) {
                    extensions.put(name, event.getExtension(name));
                }
                builder.withExtensions(extensions);
            }

            OutgoingCloudEventMetadata<?> ceMetadata = builder.build();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Flow: Publishing on channel {} CE id={} type={} source={}",
                        channelName(), event.getId(), event.getType(), event.getSource());
            }

            return outEmitter().sendMessage(Message.of(data).addMetadata(ceMetadata))
                    .subscribeAsCompletionStage();
        } catch (Exception e) {
            final CompletableFuture<Void> cf = new CompletableFuture<>();
            cf.completeExceptionally(e);
            return cf;
        }
    }

    @Override
    public void close() {
        // no-op;
    }

    protected abstract MutinyEmitter<byte[]> outEmitter();

    /**
     * Whether we should accept the event based on child's class criteria
     */
    protected abstract boolean accept(CloudEvent event);

    protected abstract String channelName();

}
