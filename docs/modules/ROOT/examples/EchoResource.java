package org.acme;

import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

@Path("/echo")
public class EchoResource {

    @Inject
    @Identifier("flow:echo-name") // <1>
    WorkflowDefinition definition;

    @GET
    public Response echo(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        final WorkflowModel model = definition.instance(Map.of("name", finalName))
                .start()
                .join();
        return Response.ok(model.asText().orElseThrow()).build();
    }
}
