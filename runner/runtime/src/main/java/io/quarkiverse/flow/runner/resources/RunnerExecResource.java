package io.quarkiverse.flow.runner.resources;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

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
import io.quarkiverse.flow.runner.model.StatusResponse;
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

    private Map<String, CompletableFuture<Boolean>> completableMap;

    @PostConstruct
    void init() {
        completableMap = new ConcurrentHashMap<>();
    }

    @POST
    @Path("/{namespace}/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Execute workflow (latest version)", description = "Executes the latest version of the specified workflow. "
            +
            "With wait=true (synchronous): blocks until workflow completes, always returns 200 OK with output. " +
            "With wait=false (asynchronous): returns immediately with 202 Accepted, workflowOutput is always null regardless of completion status. "
            +
            "This ensures a consistent API contract where async execution never includes output. " +
            "Both responses include the workflowApplicationId of the Runner instance that executed the workflow. " +
            "Namespace access is validated when namespace authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Workflow execution completed (only when wait=true). Returns workflow output.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "202", description = "Workflow accepted for async processing (when wait=false). Always returned with workflowOutput=null, regardless of whether the workflow has completed.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow not found")
    public Uni<Response> executeWorkflow(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Wait for workflow completion (default: false)") @QueryParam("wait") @DefaultValue("false") boolean wait,
            @RequestBody(description = "Workflow input data (can be object, array, string, number, or boolean)", required = false) Object request) {

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
            "With wait=false (asynchronous): returns immediately with 202 Accepted, workflowOutput is always null regardless of completion status. "
            +
            "This ensures a consistent API contract where async execution never includes output. " +
            "Both responses include the workflowApplicationId of the Runner instance that executed the workflow. " +
            "Namespace access is validated when namespace authorization is enabled.")
    @APIResponse(responseCode = "200", description = "Workflow execution completed (only when wait=true). Returns workflow output.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "202", description = "Workflow accepted for async processing (when wait=false). Always returned with workflowOutput=null, regardless of whether the workflow has completed.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionResponse.class)))
    @APIResponse(responseCode = "401", description = "Authentication required - missing or invalid credentials")
    @APIResponse(responseCode = "403", description = "Access denied to requested namespace")
    @APIResponse(responseCode = "404", description = "Workflow version not found")
    public Uni<Response> executeWorkflow(
            @Parameter(description = "Workflow namespace (access validated if namespace authorization enabled)", required = true) @PathParam("namespace") String namespace,
            @Parameter(description = "Workflow name", required = true) @PathParam("name") String name,
            @Parameter(description = "Workflow version", required = true) @PathParam("version") String version,
            @Parameter(description = "Wait for workflow completion (default: false)") @QueryParam("wait") @DefaultValue("false") boolean wait,
            @RequestBody(description = "Workflow input data (can be object, array, string, number, or boolean)", required = false) Object request) {
        final WorkflowDefinitionId id = new WorkflowDefinitionId(namespace, name, version);
        return executeWorkflow(wait, request, id, application.workflowDefinitions().get(id));
    }

    @POST
    @Path("/suspend/{instanceId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Suspend workflow execution", description = "Suspend workflow execution upon user request. "
            + "This means that once the current task is completed, the next task wont start till the user explicitly resumes the wokflow")
    @APIResponse(responseCode = "200", description = "Workflow suspended successfully")
    @APIResponse(responseCode = "304", description = "Workflow not in a state that can be suspended", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StatusResponse.class)))
    @APIResponse(responseCode = "404", description = "Workflow instance not found")
    public Uni<Response> suspendWorkflow(
            @Parameter(description = "Workflow instance id", required = true) @PathParam("instanceId") String instanceId) {
        return instanceOperation(instanceId, WorkflowInstance::suspendFuture);
    }

    @POST
    @Path("/resume/{instanceId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Resume workflow execution", description = "Resume workflow execution upon user request. "
            + "The workflow will continue with the next task.")
    @APIResponse(responseCode = "200", description = "Workflow resumed successfully")
    @APIResponse(responseCode = "304", description = "Workflow not in a state that can be resumed", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StatusResponse.class)))
    @APIResponse(responseCode = "404", description = "Workflow instance not found")
    public Uni<Response> resumeWorkflow(
            @Parameter(description = "Workflow instance id", required = true) @PathParam("instanceId") String instanceId) {
        return instanceOperation(instanceId, WorkflowInstance::resumeFuture);
    }

    @DELETE
    @Path("{instanceId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cancel workflow execution", description = "Cancel workflow execution upon user request. "
            + "It might try to cancel current task. Once that task is done, the workflow will not continue with the next task and will remain cancelled forever.")
    @APIResponse(responseCode = "200", description = "Workflow cancelled successfully")
    @APIResponse(responseCode = "304", description = "Workflow not in a state that can be cancelled", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = StatusResponse.class)))
    @APIResponse(responseCode = "404", description = "Workflow instance not found")
    public Uni<Response> cancelWorkflow(
            @Parameter(description = "Workflow instance id", required = true) @PathParam("instanceId") String instanceId) {
        return instanceOperation(instanceId, WorkflowInstance::cancelFuture);
    }

    private Uni<Response> instanceOperation(String instanceId,
            Function<WorkflowInstance, CompletableFuture<Boolean>> function) {
        return application.workflowDefinitions().values().stream().flatMap(s -> s.activeInstance(instanceId).stream())
                .map(instance -> completableMap
                        .compute(instanceId,
                                (k, v) -> v == null ? function.apply(instance) : v.thenCompose(__ -> function.apply(instance)))
                        .thenApply(v -> v ? Response.ok().build()
                                : Response.notModified().entity(StatusResponse.from(instance)).build()))
                .findFirst().map(Uni.createFrom()::completionStage)
                .orElseGet(() -> Uni.createFrom().item(notFoundResponse(instanceId)));
    }

    private Response notFoundResponse(String instanceId) {
        return Response.status(Status.NOT_FOUND).entity("Workflow instance id '" + instanceId + "' not found").build();
    }

    private Uni<Response> executeWorkflow(boolean wait, Object request, WorkflowDefinitionId id,
            WorkflowDefinition definition) {
        if (definition == null) {
            return Uni.createFrom().item(
                    Response.status(404)
                            .entity("Workflow version '" + id.namespace() + ":" + id.name() + ":" + id.version()
                                    + "' not found")
                            .build());
        }

        final String workflowApplicationId = application.id();
        final WorkflowInstance instance = definition.instance(request);
        final CompletableFuture<WorkflowModel> workflowOutput = instance.start();
        if (wait) {
            return Uni.createFrom()
                    .completionStage(workflowOutput)
                    .onItem()
                    .transform(model -> Response.ok()
                            .entity(ExecutionResponse.from(workflowApplicationId, instance, model)).build());
        }
        return Uni.createFrom()
                .item(Response.status(Response.Status.ACCEPTED)
                        .entity(ExecutionResponse.from(workflowApplicationId, instance)).build());
    }

}
