package io.quarkiverse.flow.oidc.it;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import io.serverlessworkflow.impl.WorkflowModel;

@Path("/flow-oidc")
public class OidcFlowResource {

    @Inject
    PropagationFlow propagationFlow;

    @Inject
    ClientCredentialsFlow clientCredentialsFlow;

    @GET
    @Path("/propagation")
    public Response propagation(@QueryParam("subjectToken") String subjectToken) {
        WorkflowModel model = propagationFlow.instance(Map.of("subjectToken", subjectToken)).start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/client-credentials")
    public Response clientCredentials(@QueryParam("async") boolean async) {
        if (async) {
            clientCredentialsFlow.instance().start();
            return Response.status(Response.Status.ACCEPTED).build();
        }
        WorkflowModel model = clientCredentialsFlow.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/protected")
    public Response securityIdentity(@QueryParam("async") boolean async) {
        if (async) {
            propagationFlow.instance().start();
            return Response.status(Response.Status.ACCEPTED).build();
        }
        WorkflowModel model = propagationFlow.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

}
