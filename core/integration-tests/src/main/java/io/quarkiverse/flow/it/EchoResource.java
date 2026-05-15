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
    @Identifier("flow:echo-name:0.1.0")
    Flow flow;

    @Inject
    @Identifier("flow:echo-name:0.1.0")
    WorkflowDefinition workflowDefinition;

    @Inject
    @Identifier("flow:echo-name:0.2.0")
    Flow flowV2;

    @Inject
    @Identifier("flow:echo-name:0.2.0")
    WorkflowDefinition workflowDefinitionV2;

    @Inject
    @Identifier("flow:echo-name")
    WorkflowDefinition versionlessWorkflowDefinition;

    @Inject
    @Identifier("flow:echo-name")
    Flow versionlessFlow;

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

    @GET
    @Path("/v2/from-workflow-def")
    public CompletableFuture<String> echoFromWorkflowDefV2(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return workflowDefinitionV2.instance(Map.of("name", finalName))
                .start()
                .thenApply(model -> model.asText().orElseThrow());
    }

    @GET
    @Path("/v2/from-flow")
    public CompletableFuture<String> echoFromFlowV2(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return flowV2.instance(Map.of("name", finalName))
                .start()
                .thenApply(model -> model.asText().orElseThrow());
    }

    @GET
    @Path("/from-versionless-workflow-def")
    public CompletableFuture<String> echoFromVersionlessWorkflowDef(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return versionlessWorkflowDefinition.instance(Map.of("name", finalName))
                .start()
                .thenApply(model -> model.asText().orElseThrow());
    }

    @GET
    @Path("/from-versionless-flow")
    public CompletableFuture<String> echoFromVersionlessFlow(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return versionlessFlow.instance(Map.of("name", finalName))
                .start()
                .thenApply(model -> model.asText().orElseThrow());
    }

}
