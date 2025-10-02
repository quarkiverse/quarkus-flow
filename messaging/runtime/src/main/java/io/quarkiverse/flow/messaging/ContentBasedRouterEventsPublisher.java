package io.quarkiverse.flow.messaging;

import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.smallrye.reactive.messaging.MutinyEmitter;

/**
 * A content-based router {@link EventPublisher}.
 *
 * @see <a href="https://www.enterpriseintegrationpatterns.com/patterns/messaging/ContentBasedRouter.html>Messaging Patterns -
 *      Content-Based Router</a>
 */
public abstract class ContentBasedRouterEventsPublisher implements EventPublisher {

    private static final String ENGINE_PREFIX = "io.serverlessworkflow";
    private static final Logger LOG = LoggerFactory.getLogger(ContentBasedRouterEventsPublisher.class);
    private static final EventFormat FORMAT = EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    protected boolean isLifecycleEvent(CloudEvent event) {
        final String type = event.getType();
        return type != null && type.startsWith(ENGINE_PREFIX);
    }

    @Override
    public final CompletableFuture<Void> publish(CloudEvent event) {
        if (!accept(event))
            return CompletableFuture.completedFuture(null);

        try {
            byte[] structured = FORMAT.serialize(event);
            if (LOG.isDebugEnabled())
                LOG.debug("Flow: Publishing on channel {} event={}", channelName(), new String(structured));

            return outEmitter().sendMessage(Message.of(structured)).subscribeAsCompletionStage();
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
