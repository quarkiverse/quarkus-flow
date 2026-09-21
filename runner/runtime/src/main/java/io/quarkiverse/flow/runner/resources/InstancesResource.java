package io.quarkiverse.flow.runner.resources;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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

import io.quarkiverse.flow.runner.FlowRunnerConfig;
import io.quarkiverse.flow.runner.model.ActiveInstancesResponse;
import io.quarkiverse.flow.runner.model.InstanceSnapshot;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkiverse.flow.runner.security.FlowRunnerEndpoint;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.quarkus.security.identity.SecurityIdentity;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowStatus;

/**
 * REST endpoint that exposes the in-memory active workflow instances tracked by this runner.
 *
 * <p>
 * Example:
 *
 * <pre>
 * GET /q/flow/instances
 * GET /q/flow/instances?status=RUNNING
 * GET /q/flow/{namespace}/{name}/instances
 * GET /q/flow/{namespace}/{name}/instances?status=RUNNING
 * GET /q/flow/{namespace}/{name}/{version}/instances
 * GET /q/flow/{namespace}/{name}/{version}/instances?status=RUNNING&amp;includeInput=true&amp;includeContext=true
 * </pre>
 */
@FlowRunnerEndpoint
@Path("/q/flow")
@RolesAllowed({ AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER })
@Tag(name = "Workflow Instances", description = "Query in-memory active workflow instances on this runner")
@SecurityRequirement(name = "BearerAuth")
public class InstancesResource {

    static final Set<WorkflowStatus> TERMINAL_STATUSES = EnumSet.of(
            WorkflowStatus.COMPLETED, WorkflowStatus.FAULTED, WorkflowStatus.CANCELLED);

    @Inject
    WorkflowApplication application;

    @Inject
    WorkflowDefinitionLookup definitionLookup;

    @Inject
    NamespaceAuthorizationService namespaceAuth;

    @Inject
    FlowRunnerConfig config;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/instances")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List active in-memory workflow instances", description = "Returns the workflow instances currently tracked in this runner's in-memory active instance registry, "
            + "across every registered workflow definition. "
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
            @Parameter(description = "Filter by workflow status (optional). Only non-terminal values accepted: PENDING, RUNNING, WAITING, SUSPENDED") @QueryParam("status") WorkflowStatus status,
            @Parameter(description = "Include each instance's workflow input in the response (optional, default: false)") @QueryParam("includeInput") @DefaultValue("false") boolean includeInput,
            @Parameter(description = "Include each instance's current workflow model/context in the response (optional, default: false)") @QueryParam("includeContext") @DefaultValue("false") boolean includeContext) {

        validateIfTerminalStatus(status);

        Stream<Map.Entry<WorkflowDefinitionId, WorkflowDefinition>> definitions = application.workflowDefinitions()
                .entrySet().stream();

        if (config.security().namespace().validate() && !securityIdentity.hasRole(AuthzConsts.ROLE_ADMIN)) {
            Set<String> authorizedNamespaces = namespaceAuth.getAuthorizedNamespaces();

            if (authorizedNamespaces == null || authorizedNamespaces.isEmpty()) {
                definitions = Stream.empty();
            } else if (!authorizedNamespaces.contains(AuthzConsts.ALL_NAMESPACES)) {
                definitions = definitions.filter(e -> authorizedNamespaces.contains(e.getKey().namespace()));
            }
        }

        List<InstanceSnapshot> instances = definitions
                .flatMap(e -> e.getValue().activeInstances().stream()
                        .filter(instance -> status == null || status == instance.status())
                        .map(instance -> toSnapshot(e.getKey(), instance, includeInput, includeContext)))
                .toList();

        return Response.ok(new ActiveInstancesResponse(application.id(), instances)).build();
    }

    @GET
    @Path("/{namespace}/{name}/instances")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List active in-memory workflow instances (latest version)", description = "Returns the workflow instances currently tracked in this runner's in-memory active instance "
            + "registry for the latest version of the given namespace/name. Only non-terminal instances (not yet "
            + "COMPLETED, FAULTED, or CANCELLED) are included. Namespace access is validated when namespace "
            + "authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Active in-memory workflow instances for the requested workflow on this runner", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActiveInstancesResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid status filter — terminal statuses (COMPLETED, FAULTED, CANCELLED) and unknown values are not allowed")
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow definition not found")
    public Response listActiveInstancesForWorkflow(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Filter by workflow status (optional). Only non-terminal values accepted: PENDING, RUNNING, WAITING, SUSPENDED") @QueryParam("status") WorkflowStatus status,
            @Parameter(description = "Include each instance's workflow input in the response (optional, default: false)") @QueryParam("includeInput") @DefaultValue("false") boolean includeInput,
            @Parameter(description = "Include each instance's current workflow model/context in the response (optional, default: false)") @QueryParam("includeContext") @DefaultValue("false") boolean includeContext) {

        return listActiveInstancesForDefinition(
                new WorkflowDefinitionId(namespace, name, WorkflowDefinitionLookup.LATEST), status, includeInput,
                includeContext);
    }

    @GET
    @Path("/{namespace}/{name}/{version}/instances")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List active in-memory workflow instances for a specific version", description = "Returns the workflow instances currently tracked in this runner's in-memory active instance "
            + "registry for the given namespace, name, and version. Only non-terminal instances (not yet "
            + "COMPLETED, FAULTED, or CANCELLED) are included. Namespace access is validated when namespace "
            + "authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Active in-memory workflow instances for the requested workflow on this runner", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ActiveInstancesResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid status filter — terminal statuses (COMPLETED, FAULTED, CANCELLED) and unknown values are not allowed")
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow definition not found")
    public Response listActiveInstancesForWorkflowVersion(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Workflow version", required = true) @PathParam("version") String version,
            @Parameter(description = "Filter by workflow status (optional). Only non-terminal values accepted: PENDING, RUNNING, WAITING, SUSPENDED") @QueryParam("status") WorkflowStatus status,
            @Parameter(description = "Include each instance's workflow input in the response (optional, default: false)") @QueryParam("includeInput") @DefaultValue("false") boolean includeInput,
            @Parameter(description = "Include each instance's current workflow model/context in the response (optional, default: false)") @QueryParam("includeContext") @DefaultValue("false") boolean includeContext) {

        return listActiveInstancesForDefinition(new WorkflowDefinitionId(namespace, name, version), status, includeInput,
                includeContext);
    }

    private Response listActiveInstancesForDefinition(WorkflowDefinitionId id, WorkflowStatus status, boolean includeInput,
            boolean includeContext) {

        validateIfTerminalStatus(status);

        WorkflowDefinition definition = definitionLookup.find(id);
        if (definition == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Workflow '" + id + "' not found")
                    .build();
        }

        List<InstanceSnapshot> instances = definition.activeInstances().stream()
                .filter(instance -> status == null || status == instance.status())
                .map(instance -> toSnapshot(definition.id(), instance, includeInput, includeContext))
                .toList();

        return Response.ok(new ActiveInstancesResponse(application.id(), instances)).build();
    }

    private static InstanceSnapshot toSnapshot(WorkflowDefinitionId id, WorkflowInstance instance, boolean includeInput,
            boolean includeContext) {
        return new InstanceSnapshot(
                instance.id(),
                id.name(),
                id.namespace(),
                id.version(),
                instance.status(),
                instance.startedAt(),
                includeInput ? instance.input().asJavaObject() : null,
                includeContext ? instance.context().asJavaObject() : null);
    }

    /**
     * Rejects terminal statuses as filter values; {@code null} (no filter) is always allowed.
     *
     * @throws BadRequestException if the status is a terminal status
     */
    private void validateIfTerminalStatus(WorkflowStatus status) {
        if (status != null && TERMINAL_STATUSES.contains(status)) {
            throw new BadRequestException("Terminal status '" + status
                    + "' is not a valid filter — completed, faulted, and cancelled instances are not tracked in the active registry");
        }
    }
}
