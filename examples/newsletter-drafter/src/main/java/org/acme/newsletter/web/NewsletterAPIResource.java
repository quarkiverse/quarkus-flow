package org.acme.newsletter.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.acme.newsletter.NewsletterWorkflow;
import org.acme.newsletter.domain.HumanReview;
import org.acme.newsletter.domain.NewsletterRequest;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Path("/api")
public class NewsletterAPIResource {

    @Inject
    NewsletterWorkflow newsletterWorkflow;

    @Inject
    ObjectMapper objectMapper;

    // Kafka producer bound to topic `flow-in`
    @Inject
    @Channel("flow-in-outgoing")
    Emitter<String> flowIn;

    /**
     * Starts the workflow to create a new newsletter draft.
     *
     * @param request
     *        input from the user
     *
     * @return A workflow instance that will call the agents and produce a request for review event once it's done.
     */
    @POST
    @Path("/newsletter")
    public Response newNewsletter(NewsletterRequest request) {
        final WorkflowInstance instance = newsletterWorkflow.instance(request);
        // fire and forget (agents will be called on a thread within the engine)
        instance.start();
        return Response.accepted(Map.of("instanceId", instance.id())).build();
    }

    @PUT
    @Path("/newsletter")
    public Response sendReview(HumanReview review, @HeaderParam("X-Flow-Instance-Id") String instanceId)
            throws JsonProcessingException {
        String body = objectMapper.writeValueAsString(review);

        OutgoingCloudEventMetadata<?> ceMeta = OutgoingCloudEventMetadata.builder()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("api:/newsletter"))
                .withType("org.acme.newsletter.review.done")
                .withDataContentType("application/json")
                .withExtension("flowinstanceid", instanceId)
                .build();

        flowIn.send(Message.of(body).addMetadata(ceMeta));

        return Response.accepted().build();
    }

}
