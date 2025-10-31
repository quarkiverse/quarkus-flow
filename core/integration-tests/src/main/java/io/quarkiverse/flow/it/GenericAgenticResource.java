package io.quarkiverse.flow.it;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.smallrye.common.annotation.Blocking;

@Path("/generic")
@ApplicationScoped
public class GenericAgenticResource {

    @Inject
    GenericAgenticWorkflow genericAgenticWorkflow;

    @POST
    @Blocking
    public CompletionStage<String> hello(String message) {
        return genericAgenticWorkflow
                .instance(message)
                .start()
                .thenApply(w -> w.asText().orElseThrow());
    }

}
