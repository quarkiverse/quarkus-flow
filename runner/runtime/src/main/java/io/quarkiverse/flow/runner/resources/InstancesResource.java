package io.quarkiverse.flow.runner.resources;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkiverse.flow.runner.instances.ActiveInstanceRegistry;
import io.quarkiverse.flow.runner.model.ActiveInstancesResponse;
import io.quarkiverse.flow.runner.model.InstanceSnapshot;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkiverse.flow.runner.security.FlowRunnerEndpoint;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * REST endpoint that exposes the in-memory active workflow instances tracked by this runner.
 *
 * <p>
 * Example:
 *
 * <pre>
 * GET /q/flow/instances
 * GET /q/flow/instances?workflowName=my-flow
 * GET /q/flow/instances?status=RUNNING
 * GET /q/flow/instances?workflowName=my-flow&amp;status=SUSPENDED
 * </pre>
 */
@FlowRunnerEndpoint
@Path("/q/flow/instances")
@RolesAllowed({ AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER })
@Tag(name = "Workflow Instances", description = "Query in-memory active workflow instances on this runner")
@SecurityRequirement(name = "BearerAuth")
public class InstancesResource {

    static final Set<WorkflowStatus> TERMINAL_STATUSES = EnumSet.of(
            WorkflowStatus.COMPLETED, WorkflowStatus.FAULTED, WorkflowStatus.CANCELLED);

    @Inject
    WorkflowApplication application;

    @Inject
    ActiveInstanceRegistry registry;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List active in-memory workflow instances", description = "Returns the workflow instances currently tracked in this runner's in-memory active instance registry. "
            + "Only non-terminal instances (not yet COMPLETED, FAULTED, or CANCELLED) are included. "
            + "The response also includes the applicationId of this runner. When the durable-kubernetes module is "
            + "active, this corresponds to the Kubernetes Lease holder identity used for durable sharding; otherwise "
            + "it defaults to the application name and is not guaranteed to be unique per pod. "
            + "This endpoint enables a rebalancer service to distinguish between instances actively being processed "
            + "on this pod vs. instances persisted in the DB but not running on any pod.")
    @APIResponse(responseCode = "200", description = "Active in-memory workflow instances on this runner", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActiveInstancesResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid status filter — terminal statuses (COMPLETED, FAULTED, CANCELLED) and unknown values are not allowed")
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied")
    public Response listActiveInstances(
            @Parameter(description = "Filter by workflow name (optional)") @QueryParam("workflowName") String workflowName,
            @Parameter(description = "Filter by workflow status (optional). Only non-terminal values accepted: PENDING, RUNNING, WAITING, SUSPENDED") @QueryParam("status") String status) {

        WorkflowStatus statusFilter;
        try {
            statusFilter = parseActiveStatus(status);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }

        List<InstanceSnapshot> instances = registry.activeInstances().stream()
                .filter(s -> workflowName == null || workflowName.equals(s.workflowName()))
                .filter(s -> statusFilter == null || statusFilter == s.status())
                .toList();

        return Response.ok(new ActiveInstancesResponse(application.id(), instances)).build();
    }

    /**
     * Parses the status query parameter, rejecting terminal statuses and unknown values.
     *
     * @return the parsed {@link WorkflowStatus}, or {@code null} if {@code status} is blank/null (no filter)
     * @throws IllegalArgumentException if the value is unknown or a terminal status
     */
    private WorkflowStatus parseActiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        WorkflowStatus parsed;
        try {
            parsed = WorkflowStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status value: '" + status
                    + "'. Valid non-terminal values are: PENDING, RUNNING, WAITING, SUSPENDED");
        }
        if (TERMINAL_STATUSES.contains(parsed)) {
            throw new IllegalArgumentException("Terminal status '" + parsed
                    + "' is not a valid filter — completed, faulted, and cancelled instances are not tracked in the active registry");
        }
        return parsed;
    }
}
