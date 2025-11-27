package org.acme;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import io.quarkiverse.flow.Flow;
import io.smallrye.common.annotation.Identifier;

@Path("/echo")
public class EchoResource {

    @Inject
    @Identifier("company.EchoName") // <1>
    Flow flow;

    @GET
    public CompletionStage<String> echo(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return flow.instance(Map.of("name", finalName))
                .start()
                .thenApply(result -> result.asText().orElseThrow());
    }
}
