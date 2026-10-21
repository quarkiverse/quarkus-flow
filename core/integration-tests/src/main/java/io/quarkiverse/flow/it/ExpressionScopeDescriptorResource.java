package io.quarkiverse.flow.it;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkiverse.flow.Flow;
import io.smallrye.common.annotation.Identifier;

@Path("/expression-scope-descriptors")
public class ExpressionScopeDescriptorResource {

    @Inject
    @Identifier("flow:expression-scope-descriptors:1.0.0")
    Flow describeContextFlow;

    @Inject
    @Identifier("flow:for-task-name-collision:1.0.0")
    Flow forTaskNameCollisionFlow;

    @GET
    @Path("/direct")
    public CompletableFuture<String> direct() {
        return describeContextFlow.instance(Map.of())
                .start()
                .thenApply(model -> model.asText().orElseThrow());
    }

    @GET
    @Path("/for-loop")
    public CompletableFuture<String> forLoop() {
        return forTaskNameCollisionFlow
                .instance(Map.of("items", List.of(Map.of("name", "alpha"), Map.of("name", "beta"))))
                .start()
                .thenApply(model -> String.valueOf(model.asJavaObject()));
    }
}
