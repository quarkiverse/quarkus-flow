package io.quarkiverse.flow.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;

@Path("/generic")
@ApplicationScoped
public class GenericAgenticResource {

    @Inject
    GenericAgenticWorkflow genericAgenticWorkflow;

    @POST
    @Blocking
    public Uni<String> hello(String message) {
        return genericAgenticWorkflow
                .startInstance(message)
                .onItem()
                .transform(wf -> wf.as(String.class).orElseThrow());
    }

}
