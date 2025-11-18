package io.quarkiverse.flow.it;

import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

@Path("/echo")
public class EchoResource {

    @Inject
    WorkflowApplication app;

    @Inject
    @Identifier("default:echo-name") // identifier from resource/flow/echo-name.yaml
    WorkflowDefinition definition;

    @GET
    public Response echo(@QueryParam("name") String name) {
        WorkflowModel model = app.workflowDefinition(definition.workflow())
                .instance(Map.of(
                        "name", Objects.requireNonNullElse(name, "(Duke)")))
                .start()
                .join();
        return Response.ok(model.asText().orElseThrow())
                .build();
    }
}
