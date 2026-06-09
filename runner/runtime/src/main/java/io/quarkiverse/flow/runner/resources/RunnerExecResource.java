package io.quarkiverse.flow.runner.resources;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkiverse.flow.internal.WorkflowVersionComparator;
import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.quarkiverse.flow.runner.security.AuthzConsts;
import io.quarkiverse.flow.runner.security.FlowRunnerEndpoint;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.mutiny.Uni;

@FlowRunnerEndpoint
@Path("/q/flow/exec")
@RolesAllowed({ AuthzConsts.ROLE_ADMIN, AuthzConsts.ROLE_INVOKER })
@Tag(name = "Workflow Execution", description = "Execute and manage workflow instances")
@SecurityRequirement(name = "BearerAuth")
public class RunnerExecResource {

    @Inject
    WorkflowApplication application;

    @POST
    @Path("/{namespace}/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Execute workflow (latest version)", description = "Executes the latest version of the specified workflow. "
            +
            "With wait=true (synchronous): blocks until workflow completes, always returns 200 OK with output. " +
            "With wait=false (asynchronous): returns immediately. If workflow already completed, returns 200 OK with output. " +
            "If still running, returns 202 Accepted without output. " +
            "Namespace access is validated when namespace authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Workflow execution completed. Returned when: (1) wait=true and workflow finished, or (2) wait=false but workflow completed before response.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "202", description = "Workflow execution accepted for processing (wait=false and still running). Check workflowOutput field - will be null if pending.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow not found")
    public Uni<Response> executeWorkflow(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Wait for workflow completion (default: false)") @QueryParam("wait") @DefaultValue("false") boolean wait,
            @RequestBody(description = "Workflow input data", required = false) Map<String, Object> request) {

        final Optional<WorkflowDefinition> definition = application.workflowDefinitions().entrySet().stream()
                .filter(entry -> name.equals(entry.getKey().name()) && namespace.equals(entry.getKey().namespace()))
                .max(new WorkflowVersionComparator())
                .map(Map.Entry::getValue);

        return executeWorkflow(wait, request, new WorkflowDefinitionId(namespace, name, "latest"),
                definition.orElse(null));
    }

    @POST
    @Path("/{namespace}/{name}/{version}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Execute workflow (specific version)", description = "Executes a specific version of the workflow. " +
            "With wait=true (synchronous): blocks until workflow completes, always returns 200 OK with output. " +
            "With wait=false (asynchronous): returns immediately. If workflow already completed, returns 200 OK with output. " +
            "If still running, returns 202 Accepted without output. " +
            "Namespace access is validated when namespace authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Workflow execution completed. Returned when: (1) wait=true and workflow finished, or (2) wait=false but workflow completed before response.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "202", description = "Workflow execution accepted for processing (wait=false and still running). Check workflowOutput field - will be null if pending.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow version not found")
    public Uni<Response> executeWorkflow(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Workflow version", required = true) @PathParam("version") String version,
            @Parameter(description = "Wait for workflow completion (default: false)") @QueryParam("wait") @DefaultValue("false") boolean wait,
            @RequestBody(description = "Workflow input data", required = false) Map<String, Object> request) {
        final WorkflowDefinitionId id = new WorkflowDefinitionId(namespace, name, version);
        final WorkflowDefinition definition = application.workflowDefinitions().get(id);
        return executeWorkflow(wait, request, id, definition);
    }

    private Uni<Response> executeWorkflow(boolean wait, Map<String, Object> request, WorkflowDefinitionId id,
            WorkflowDefinition definition) {
        if (definition == null) {
            return Uni.createFrom().item(
                    Response.status(404)
                            .entity("Workflow version '" + id.namespace() + ":" + id.name() + ":" + id.version()
                                    + "' not found")
                            .build());
        }

        final WorkflowInstance instance = definition.instance(request);
        final CompletableFuture<WorkflowModel> workflowOutput = instance.start();
        if (wait) {
            return Uni.createFrom()
                    .completionStage(workflowOutput)
                    .onItem()
                    .transform(model -> Response.ok().entity(ExecutionResponse.from(instance, model)).build());
        }
        final ExecutionResponse response = ExecutionResponse.from(instance);
        return Uni.createFrom()
                .item(Response.status(response.workflowOutput() == null ? Response.Status.ACCEPTED : Response.Status.OK)
                        .entity(response).build());
    }

}
