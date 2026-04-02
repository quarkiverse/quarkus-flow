package org.acme.flow.durable.kube;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/workflow")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DemoWorkflowResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoWorkflowResource.class);

    @Inject
    DemoWorkflow workflow;

    @ConfigProperty(name = "org.acme.flow.durable.kube.sleep-seconds")
    int delaySeconds;

    @Blocking
    @POST
    public Response executeWorkflow(Map<String, Object> input) {
        if (input == null) {
            input = Map.of();
        }

        try {
            LOGGER.info("Starting durable workflow execution remotely...");

            Optional<Map<String, Object>> output = workflow.startInstance(input)
                    .await().atMost(Duration.ofSeconds(delaySeconds + 5))
                    .asMap();

            if (output.isPresent()) {
                LOGGER.info("Workflow completed successfully in {} ms", output.get().get("durationMillis"));
                return Response.ok(output.get()).build();
            } else {
                return Response.status(Response.Status.NO_CONTENT).entity(Map.of("message", "No output returned")).build();
            }
        } catch (Exception e) {
            LOGGER.error("Workflow execution failed", e);
            return Response.serverError()
                    .entity(Map.of("error", "Workflow failed", "details", e.getMessage()))
                    .build();
        }
    }
}