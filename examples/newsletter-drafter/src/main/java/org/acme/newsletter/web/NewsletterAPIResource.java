package org.acme.newsletter.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.acme.newsletter.NewsletterWorkflow;
import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterRequest;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/api")
public class NewsletterAPIResource {

    private static final JsonFormat CE_JSON = (JsonFormat) EventFormatProvider.getInstance()
            .resolveFormat(JsonFormat.CONTENT_TYPE);

    @Inject
    NewsletterWorkflow newsletterWorkflow;

    @Inject
    ObjectMapper objectMapper;

    // Kafka producer bound to topic `flow-in`
    @Inject
    @Channel("flow-in-outgoing")
    Emitter<byte[]> flowIn;

    /**
     * Starts the workflow to create a new newsletter draft.
     *
     * @param request input from the user
     * @return A workflow instance that will call the agents and produce a request for review event once it's done.
     */
    @POST
    @Path("/newsletter")
    public Response newNewsletter(NewsletterRequest request) {
        final WorkflowInstance instance = newsletterWorkflow.instance(request);
        // fire and forget (agents will be called on a thread within the engine)
        CompletableFuture.runAsync(instance::start);
        return Response.accepted(Map.of("instanceId", instance.id())).build();
    }

    @PUT
    @Path("/newsletter/{instanceId}")
    public Response sendReview(@PathParam("instanceId") String instanceId, HumanReview review) throws JsonProcessingException {
        byte[] body = objectMapper.writeValueAsBytes(review);

        CloudEvent ce = CloudEventBuilder.v1().withId(UUID.randomUUID().toString())
                .withSource(URI.create("api:/newsletter"))
                .withType("org.acme.newsletter.review.done")
                .withDataContentType("application/json")
                .withExtension("flowinstanceid", instanceId) // <- Required for correlation!
                .withData(body).build();

        byte[] ceBytes = CE_JSON.serialize(ce);
        flowIn.send(ceBytes);

        return Response.accepted().build();
    }

}
