package io.quarkiverse.flow.runner.resources;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.flow.internal.WorkflowVersionComparator;
import io.quarkiverse.flow.runner.model.ExecutionResponse;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.mutiny.Uni;

@Path("/runner/exec")
public class RunnerExecResource {

    @Inject
    WorkflowApplication application;

    @POST
    @Path("/{namespace}/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> executeWorkflow(
            @PathParam("namespace") String namespace,
            @PathParam("name") String name,
            @QueryParam("wait") @DefaultValue("false") boolean wait,
            Map<String, Object> request) {

        final Optional<WorkflowDefinition> definition = application.workflowDefinitions().entrySet().stream()
                .filter(entry -> name.equals(entry.getKey().name()) && namespace.equals(entry.getKey().namespace()))
                .max(new WorkflowVersionComparator())
                .map(Map.Entry::getValue);

        return executeWorkflow(wait, request, new WorkflowDefinitionId(namespace, name, "latest"), definition.orElse(null));
    }

    @POST
    @Path("/{namespace}/{name}/{version}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> executeWorkflow(
            @PathParam("namespace") String namespace,
            @PathParam("name") String name,
            @PathParam("version") String version,
            @QueryParam("wait") @DefaultValue("false") boolean wait,
            Map<String, Object> request) {
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
        return Uni.createFrom()
                .item(Response.status(Response.Status.ACCEPTED).entity(ExecutionResponse.from(instance)).build());
    }

    @GET
    @Path("/{id}/status")
    public Response getStatus(@PathParam("id") String id) {
        return Response.ok().build();
    }
    // Returns 200 with status details for active executions
    // Returns 404 if execution not found (never existed, or completed/aborted and removed from memory)
    // Execution ID is globally unique, so namespace/name/version not needed

    @PUT
    @Path("/{id}/resume")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resumeExecution(
            @PathParam("id") String id,
            JsonNode payload) {
        return Response.ok().build();
    }
    // Returns 200 OK or 404
    // Execution ID is globally unique

    @DELETE
    @Path("/{id}")
    public Response abortExecution(@PathParam("id") String id) {
        return Response.ok().build();
    }
    // Returns 204 No Content or 404
    // Execution ID is globally unique

}
