package io.quarkiverse.flow.it;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

@Path("/echo")
public class EchoResource {

    @Inject
    @Identifier("flow.EchoName")
    Flow flow;

    @Inject
    @Identifier("flow:echo-name")
    WorkflowDefinition workflowDefinition;

    @GET
    @Path("/from-workflow-def")
    public CompletableFuture<String> echoFromWorkflowDef(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return workflowDefinition.instance(Map.of("name", finalName))
                .start()
                .thenApply(model -> model.asText().orElseThrow());
    }

    @GET
    @Path("/from-flow")
    public CompletableFuture<String> echoFromFlow(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return flow.instance(Map.of("name", finalName))
                .start()
                .thenApply(model -> model.asText().orElseThrow());
    }

}
