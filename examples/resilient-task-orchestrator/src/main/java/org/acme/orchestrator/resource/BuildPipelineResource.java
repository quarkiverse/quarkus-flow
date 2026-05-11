package org.acme.orchestrator.resource;

import io.serverlessworkflow.impl.WorkflowInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.orchestrator.model.BuildSpec;
import org.acme.orchestrator.model.TaskState;
import org.acme.orchestrator.service.TaskStateStore;
import org.acme.orchestrator.workflow.CoordinatorWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST API for triggering and monitoring build pipelines.
 */
@Path("/api/builds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BuildPipelineResource {
    private static final Logger LOG = LoggerFactory.getLogger(BuildPipelineResource.class);

    @Inject
    CoordinatorWorkflow coordinatorWorkflow;

    @Inject
    TaskStateStore stateStore;

    /**
     * Start a new build pipeline.
     */
    @POST
    @Path("/start")
    public Response startBuild(BuildSpec spec) {
        LOG.info("Starting build pipeline for project: {}", spec.projectName());

        try {
            // Clear previous state for clean run
            stateStore.clear();

            // Start the coordinator workflow
            WorkflowInstance instance = coordinatorWorkflow.instance(spec);
            instance.start();

            return Response.accepted()
                    .entity(Map.of(
                            "buildId", instance.id(),
                            "status", "STARTED",
                            "project", spec.projectName(),
                            "tasks", spec.tasks()))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to start build pipeline", e);
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Start a simple build with default tasks.
     */
    @POST
    @Path("/start/{projectName}")
    public Response startDefaultBuild(@PathParam("projectName") String projectName) {
        BuildSpec spec = BuildSpec.createDefault(projectName);
        return startBuild(spec);
    }

    /**
     * Get status of all tasks.
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        Map<String, TaskState> allStates = stateStore.getAll();
        return Response.ok(allStates).build();
    }

    /**
     * Get status of a specific task.
     */
    @GET
    @Path("/status/{taskId}")
    public Response getTaskStatus(@PathParam("taskId") String taskId) {
        TaskState state = stateStore.get(taskId);
        if (state == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Task not found: " + taskId))
                    .build();
        }
        return Response.ok(state).build();
    }

    /**
     * Resume a failed build (demonstrates resume capability).
     */
    @POST
    @Path("/resume/{projectName}")
    public Response resumeBuild(
            @PathParam("projectName") String projectName,
            BuildSpec spec) {
        LOG.info("Resuming build for project: {}", projectName);

        // In a real system, we'd load the previous build state
        // For this example, we just restart with existing task states
        return startBuild(spec);
    }
}
