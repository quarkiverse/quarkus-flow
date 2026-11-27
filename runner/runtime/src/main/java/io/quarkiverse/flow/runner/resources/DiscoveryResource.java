package io.quarkiverse.flow.runner.resources;

import java.util.Optional;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkiverse.flow.persistence.spi.InstanceLocation;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkiverse.flow.runner.security.FlowRunnerEndpoint;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;

/**
 * REST endpoint that resolves where a workflow instance lives across shards.
 *
 * <p>
 * Provides a workflow-scoped, cross-shard lookup of instance location (owning applicationId and
 * lightweight metainfo) by first checking the in-memory active registry on this pod, then falling
 * back to the durable persistence backend if configured.
 *
 * <p>
 * Example:
 *
 * <pre>
 * GET /q/flow/{namespace}/{name}/{version}/discovery/{instanceId}
 * </pre>
 */
@FlowRunnerEndpoint
@Path("/q/flow")
@Tag(name = "Workflow Instance Discovery", description = "Resolve workflow instance location across shards")
@SecurityRequirement(name = "BearerAuth")
public class DiscoveryResource {

    @Inject
    WorkflowApplication application;

    @Inject
    WorkflowDefinitionLookup definitionLookup;

    @Inject
    Instance<InstanceLocator> locator;

    @GET
    @Path("/{namespace}/{name}/{version}/discovery/{instanceId}")
    @RolesAllowed({ AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER, AuthzConsts.ROLE_SERVICE_READER })
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Resolve workflow instance location", description = "Resolves where a workflow instance lives across the shards of the runtime serving that workflow. "
            + "First checks the in-memory active registry on this pod, then falls back to the durable persistence backend if configured. "
            + "Returns the owning applicationId. "
            + "Namespace access is validated when namespace authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Instance location resolved", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = InstanceLocation.class)))
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow instance not found")
    public Response discover(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Workflow version", required = true) @PathParam("version") String version,
            @Parameter(description = "Workflow instance id", required = true) @PathParam("instanceId") String instanceId) {

        // Check in-memory active registry (memory first)
        WorkflowDefinitionId definitionId = new WorkflowDefinitionId(namespace, name, version);
        WorkflowDefinition definition = definitionLookup.find(definitionId);
        if (definition != null) {
            Optional<WorkflowInstance> activeInstance = definition.activeInstance(instanceId);
            if (activeInstance.isPresent()) {
                WorkflowInstance instance = activeInstance.get();
                InstanceLocation location = new InstanceLocation(namespace, name, version, instanceId,
                        application.id());
                return Response.ok(location).build();
            }
        }

        // Fall back to persistence backend (if configured)
        if (locator.isResolvable()) {
            Optional<InstanceLocation> location = locator.get().locate(namespace, name, version, instanceId);
            if (location.isPresent()) {
                return Response.ok(location.get()).build();
            }
        }

        // Not found anywhere
        return Response.status(Response.Status.NOT_FOUND)
                .entity("Workflow instance '" + instanceId + "' not found")
                .build();
    }
}
