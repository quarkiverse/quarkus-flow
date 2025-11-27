package org.acme.newsletter.web;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to Kafka topic "flow-out" and forwards only the critic "review required" events
 * to all connected WebSocket clients at /ws/newsletter.
 */
@ApplicationScoped
public class NewsletterOutBridge {

    @Inject
    NewsletterReviewCache cache;

    private static final JsonFormat CE_JSON =
            (JsonFormat) EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
    private static final Logger LOG = LoggerFactory.getLogger(NewsletterOutBridge.class.getName());

    // match the type emitted by our workflow: "org.acme.email.review.required"
    private static final String REVIEW_REQUIRED_TYPE = "org.acme.email.review.required";

    @Incoming("flow-out-incoming")
    public void onFlowOut(byte[] record) {
        try {
            CloudEvent ce = CE_JSON.deserialize(record);
            if (ce == null || ce.getType() == null) return;

            if (REVIEW_REQUIRED_TYPE.equals(ce.getType())) {
                byte[] data = ce.getData() != null ? ce.getData().toBytes() : null;
                // If there's no data, send a minimal envelope so the UI can handle it.
                String json = (data == null || data.length == 0)
                        ? "{\"type\":\""+REVIEW_REQUIRED_TYPE+"\",\"payload\":null}"
                        : new String(data, StandardCharsets.UTF_8);

                cache.add(json);
                NewsletterUpdatesSocket.broadcast(json);
            }
        } catch (Exception ex) {
            LOG.error("Failed to consume event {}", new String(record), ex);
        }
    }
}
