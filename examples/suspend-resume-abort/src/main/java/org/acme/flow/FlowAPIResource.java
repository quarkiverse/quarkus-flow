package org.acme.flow;

import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.Map;
import java.util.function.Function;

@Path("/api/flow")
@ApplicationScoped
public class FlowAPIResource {

    @Inject
    @Identifier("example:SwitchLoopWait")
    WorkflowDefinition flow;

    /**
     * Start a new workflow instance. The instance id is returned in the response.
     *
     * @param request The request body containing the parameters to start the workflow instance.
     * @return Response with status 202 and the instance id in the body if the instance was started, 400 if the request body is
     *         invalid.
     */
    @POST
    @Path("/start")
    public Response start(FlowRequest request) {
        final WorkflowInstance instance = flow.instance(request);
        instance.start();
        return Response.accepted(Map.of("instanceId", instance.id())).build();
    }

    /**
     * Resume a workflow instance. The instance id is passed as path parameter.
     *
     * @param instanceId The id of the workflow instance to resume.
     * @return Response with status 200 if the instance was resumed, 304 if it was not in a state that can be resumed, 404 if
     *         the instance id does not exist.
     */
    @POST
    @Path("/resume/{instanceId}")
    public Response resume(@PathParam("instanceId") String instanceId) {
        return instanceOperation(instanceId, WorkflowInstance::resume);
    }

    /**
     * Suspend a workflow instance. The instance id is passed as path parameter.
     *
     * @param instanceId The id of the workflow instance to suspend.
     * @return Response with status 200 if the instance was suspended, 304 if it was not in a state that can be suspended, 404
     *         if the instance id does not exist.
     */
    @POST
    @Path("/suspend/{instanceId}")
    public Response suspend(@PathParam("instanceId") String instanceId) {
        return instanceOperation(instanceId, WorkflowInstance::suspend);
    }

    /**
     * Cancel a workflow instance. The instance id is passed as path parameter.
     *
     * @param instanceId The id of the workflow instance to cancel.
     * @return Response with status 200 if the instance was canceled, 304 if it was not in a state that can be canceled, 404 if
     *         the instance id does not exist.
     */
    @POST
    @Path("/cancel/{instanceId}")
    public Response cancel(@PathParam("instanceId") String instanceId) {
        return instanceOperation(instanceId, WorkflowInstance::cancel);
    }

    private Response instanceOperation(String instanceId, Function<WorkflowInstance, Boolean> function) {
        return flow.activeInstance(instanceId)
                .map(instance -> function.apply(instance) ? Response.ok().build() : Response.notModified().build())
                .orElseGet(() -> notFoundResponse(instanceId));
    }

    private Response notFoundResponse(String instanceId) {
        return Response.status(Status.NOT_FOUND).entity("Instance Id " + instanceId + " does not exist").build();
    }
}
