package ilove.quark.us;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@Path("/hello-flow")
@ApplicationScoped
public class HelloResource {

    @Inject
    HelloWorkflow hello; // inject the Flow subclass

    @GET
    @ResponseStatus(200)
    public CompletionStage<Message> hello() {
        return hello
                .startInstance(Map.of())                // convenience on Flow
                .thenApply(w -> w.as(Message.class).orElseThrow());
    }

}
