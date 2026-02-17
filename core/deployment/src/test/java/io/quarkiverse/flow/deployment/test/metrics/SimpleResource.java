package io.quarkiverse.flow.deployment.test.metrics;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/simple-resource")
public class SimpleResource {

    @Inject
    SimpleFlow flow;

    @POST
    public void start() {
        flow.instance(Map.of("message", "hello"))
                .start()
                .join();
    }
}
