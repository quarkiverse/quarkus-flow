package org.acme.newsletter.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.acme.newsletter.NewsletterWorkflow;
import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterInput;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Path("/api")
public class NewsletterAPIResource {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    NewsletterWorkflow newsletterWorkflow;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    NewsletterReviewCache cache;

    // Kafka producer bound to topic `flow-in`
    @Inject
    @Channel("flow-in-outgoing")
    Emitter<byte[]> flowIn;

    /**
     * Starts the workflow to create a new newsletter draft.
     *
     * @param newsletter input from the user
     * @return A workflow instance that will call the agents and produce a request for review event once it's done.
     * @throws JsonProcessingException in case of an error converting the object into a JSON string for the agent.
     */
    @POST
    @Path("/newsletter")
    public Response newNewsletter(NewsletterInput newsletter) throws JsonProcessingException {
        // Agents expect a JSON string; hand that to the workflow input
        final String payload = objectMapper.writeValueAsString(newsletter);
        final WorkflowInstance instance = newsletterWorkflow.instance(payload);
        // fire and forget (agents will be called on a thread within the engine)
        instance.start();
        return Response.accepted(Map.of("instanceId", instance.id())).build();
    }

    // TODO: add {id} (which can be the workflow instance ID) to the path - for now, let's simplify

    @PUT
    @Path("/newsletter")
    public Response sendReview(HumanReview review) throws JsonProcessingException {
        byte[] body = objectMapper.writeValueAsBytes(review);

        CloudEvent ce = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("api:/newsletter"))
                .withType("org.acme.newsletter.review.done")
                .withDataContentType("application/json")
                .withData(body)
                .build();

        byte[] ceBytes = CE_JSON.serialize(ce);
        flowIn.send(ceBytes);

        return Response.accepted().build();
    }

    /** GET /api/newsletter/reviews/latest?limit=1  -> newest-first array of items */
    @GET
    @Path("/newsletter/reviews/latest")
    public Response latestReviews(@QueryParam("limit") @DefaultValue("1") int limit) {
        limit = Math.max(1, Math.min(50, limit));
        List<Map<String, String>> items = cache.latest(limit).stream()
                .map(it -> Map.of(
                        "received", it.received.toString(),
                        "payload", it.json))
                .toList();

        return Response.ok(items).build();
    }

}
