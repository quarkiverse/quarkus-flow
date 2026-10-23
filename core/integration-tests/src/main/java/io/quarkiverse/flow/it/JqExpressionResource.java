package io.quarkiverse.flow.it;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import io.quarkiverse.flow.Flow;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;

@Path("/jq-expression")
public class JqExpressionResource {

    @Inject
    @Identifier("flow:jq-expression:0.1.0")
    Flow flow;

    @GET
    @NonBlocking
    public Uni<Message> upperCaseName(@QueryParam("name") @DefaultValue("duke") String name) {
        return flow.startInstance(Map.of("name", name))
                .onItem()
                .transform(model -> model.as(Message.class).orElseThrow());
    }
}
