package io.quarkiverse.flow.runner.resources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkiverse.flow.internal.WorkflowVersionComparator;
import io.quarkiverse.flow.runner.model.WorkflowDefinitionHeader;
import io.quarkiverse.flow.runner.model.WorkflowFormatUtils;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkiverse.flow.runner.security.FlowRunnerEndpoint;
import io.quarkiverse.flow.runner.security.NamespaceAuthorizationService;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowWriter;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;

@FlowRunnerEndpoint
@Path("/q/flow/definitions")
@RolesAllowed({ AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER })
@Tag(name = "Workflow Definitions", description = "Browse and retrieve workflow definitions")
@SecurityRequirement(name = "BearerAuth")
public class DefinitionResource {

    @Inject
    WorkflowApplication application;

    @Inject
    NamespaceAuthorizationService namespaceAuth;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List workflow definitions", description = "Returns metadata for all registered workflow definitions. "
            +
            "Results are automatically filtered by authorized namespaces when namespace validation is enabled. " +
            "Optional namespace query parameter further restricts results to a specific namespace. " +
            "Each definition includes HATEOAS links for discovery.")
    @APIResponse(responseCode = "200", description = "List of workflow definition metadata (filtered by authorized namespaces)", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WorkflowDefinitionHeader[].class)))
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace (when namespace query parameter is provided)")
    public Response listDefinitions(
            @Parameter(description = "Filter workflows by specific namespace (optional). " +
                    "If provided and namespace validation is enabled, user must have access to this namespace. " +
                    "If omitted, returns workflows from all authorized namespaces.") @QueryParam("namespace") String namespace) {
        Stream<WorkflowDefinition> definitions = application.workflowDefinitions().values().stream();

        if (namespace != null) {
            // Specific filter for the required namespace
            // our ContainerFilter should block access to the method if user doesn't have access to it
            definitions = definitions.filter(def -> namespace.equals(def.workflow().getDocument().getNamespace()));
        } else {
            // Filter by namespaces that our user has access
            Set<String> authorizedNamespaces = namespaceAuth.getAuthorizedNamespaces();
            if (authorizedNamespaces != null && !authorizedNamespaces.isEmpty()) {
                definitions = definitions
                        .filter(def -> authorizedNamespaces.contains(def.workflow().getDocument().getNamespace()));
            }
        }

        return Response.ok(definitions
                .map(definition -> WorkflowDefinitionHeader.from(definition.workflow()))
                .toList())
                .build();
    }

    @GET
    @Path("/{namespace}/{name}")
    @Produces({ MediaType.APPLICATION_JSON, "application/yaml" })
    @Operation(summary = "Get workflow definition (latest version)", description = "Returns the full workflow definition for the latest version in the requested format (JSON or YAML). "
            +
            "Use the Accept header to specify format: application/json or application/yaml. " +
            "Namespace access is validated when namespace authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Workflow definition (latest version) in requested format", content = {
            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
            @Content(mediaType = "application/yaml", schema = @Schema(implementation = String.class))
    })
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow definition not found")
    @APIResponse(responseCode = "415", description = "Unsupported Media Type - use Accept: application/json or application/yaml")
    public Response getLatestDefinition(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Context HttpHeaders headers) {
        final WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);

        // Find latest version by comparing all versions for this namespace:name
        Optional<String> workflowDocument = application.workflowDefinitions().entrySet().stream()
                .filter(entry -> namespace.equals(entry.getKey().namespace()) && name.equals(entry.getKey().name()))
                .max(new WorkflowVersionComparator())
                .map(entry -> parseWorkflowDocument(entry.getValue(), format));

        if (workflowDocument.isPresent()) {
            return Response.ok(workflowDocument.get()).type(WorkflowFormatUtils.formatToMediaType(format)).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/{namespace}/{name}/{version}")
    @Produces({ MediaType.APPLICATION_JSON, "application/yaml" })
    @Operation(summary = "Get workflow definition (specific version)", description = "Returns the full workflow definition for a specific version in the requested format (JSON or YAML). "
            +
            "Use the Accept header to specify format: application/json or application/yaml. " +
            "Namespace access is validated when namespace authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Workflow definition in requested format", content = {
            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)),
            @Content(mediaType = "application/yaml", schema = @Schema(implementation = String.class))
    })
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow definition not found")
    @APIResponse(responseCode = "415", description = "Unsupported Media Type - use Accept: application/json or application/yaml")
    public Response getDefinition(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Workflow version", required = true) @PathParam("version") String version,
            @Context HttpHeaders headers) {
        final WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);
        Optional<String> workflowDocument = application.workflowDefinitions().values().stream()
                .filter(def -> {
                    final Document doc = def.workflow().getDocument();
                    return namespace.equals(doc.getNamespace()) &&
                            name.equals(doc.getName()) &&
                            version.equals(doc.getVersion());
                })
                .findFirst()
                .map(def -> parseWorkflowDocument(def, format));

        if (workflowDocument.isPresent()) {
            return Response.ok(workflowDocument.get()).type(WorkflowFormatUtils.formatToMediaType(format)).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private String parseWorkflowDocument(WorkflowDefinition workflowDefinition, WorkflowFormat format) {
        try {
            return WorkflowWriter.workflowAsString(workflowDefinition.workflow(), format);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Impossible to parse Workflow to a readable String format (" + format + "): " + e.getMessage(), e);
        }
    }
}
