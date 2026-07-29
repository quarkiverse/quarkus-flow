package org.acme.newsletter.web;

import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to Kafka topic "flow-out" and forwards only the critic "review required" events to all connected WebSocket
 * clients at /ws/newsletter.
 */
@ApplicationScoped
public class NewsletterOutBridge {

    private static final Logger LOG = LoggerFactory.getLogger(NewsletterOutBridge.class);

    // match the type emitted by our workflow: "org.acme.email.review.required"
    private static final String REVIEW_REQUIRED_TYPE = "org.acme.email.review.required";

    @Incoming("flow-out-incoming")
    public CompletionStage<Void> onFlowOut(Message<byte[]> msg) {
        try {
            CloudEventMetadata<?> ceMeta = msg.getMetadata(CloudEventMetadata.class).orElse(null);
            if (ceMeta == null || ceMeta.getType() == null)
                return msg.ack();

            if (REVIEW_REQUIRED_TYPE.equals(ceMeta.getType())) {
                byte[] data = msg.getPayload();
                // If there's no data, send a minimal envelope so the UI can handle it.
                String json = (data == null || data.length == 0)
                        ? "{\"type\":\"" + REVIEW_REQUIRED_TYPE + "\",\"payload\":null}"
                        : new String(data, StandardCharsets.UTF_8);

                LOG.info("Received review (workflow instance: {}) required event: {}",
                        ceMeta.getExtension("flowinstanceid"), json);

                NewsletterUpdatesSocket.broadcast(json);
            }
        } catch (Exception ex) {
            LOG.error("Failed to consume event", ex);
        }
        return msg.ack();
    }
}
