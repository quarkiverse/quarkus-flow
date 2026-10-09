package io.quarkiverse.flow.opentelemetry.it;

import java.util.Map;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/** Target of the HTTP call tasks used by {@code HttpCallTaskSpanIT}. */
@Path("http-echo")
public class HttpEchoResource {
    @POST
    @Path("{name}")
    public Map<String, Object> echo(@PathParam("name") String name, Map<String, Object> body) {
        return Map.of("echo", name);
    }
}
