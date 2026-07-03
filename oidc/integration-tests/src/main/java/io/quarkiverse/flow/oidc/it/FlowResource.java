package io.quarkiverse.flow.oidc.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import io.serverlessworkflow.impl.WorkflowModel;

@Path("/quarkus-flow")
public class FlowResource {

    @Inject
    ClientCredentialsFlow clientCredentials;

    @Inject
    ScopeReadImagesFlow scopeReadImages;

    @Inject
    ScopeWriteImagesFlow scopeWriteImages;

    @Inject
    ConfigOverrideFlow configOverride;

    @Inject
    EndpointAImagesFlow endpointAImages;

    @Inject
    EndpointBImagesFlow endpointBImages;

    @GET
    @Path("/images")
    public Response listImages() {
        WorkflowModel model = clientCredentials.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/config-override/images")
    public Response listConfigOverrideImages() {
        WorkflowModel model = configOverride.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/endpoint/a/images")
    public Response listEndpointAImages() {
        WorkflowModel model = endpointAImages.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/endpoint/b/images")
    public Response listEndpointBImages() {
        WorkflowModel model = endpointBImages.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/scoped/read/images")
    public Response listScopedReadImages() {
        WorkflowModel model = scopeReadImages.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/scoped/write/images")
    public Response listScopedWriteImages() {
        WorkflowModel model = scopeWriteImages.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }
}
