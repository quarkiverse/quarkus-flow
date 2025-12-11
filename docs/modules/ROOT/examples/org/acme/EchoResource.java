package org.acme;

import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import io.quarkiverse.flow.Flow;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

@Path("/echo")
public class EchoResource {

    @Inject
    @Identifier("company.EchoName") // <1>
    Flow flow;

    @GET
    public Uni<String> echo(@QueryParam("name") String name) {
        final String finalName = Objects.requireNonNullElse(name, "(Duke)");
        return flow.startInstance(Map.of("name", finalName))
                .onItem()
                .transform(wf -> wf.asText().orElseThrow());
    }
}
