package ilove.quark.us;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.ResponseStatus;

import io.smallrye.mutiny.Uni;

import java.util.Map;

@Path("/hello-flow")
@ApplicationScoped
public class HelloResource {

    @Inject
    HelloWorkflow hello; // inject the Flow subclass

    @GET
    @ResponseStatus(200)
    public Uni<Message> hello() {
        return hello
                .startInstance()                // convenience on Flow
                .onItem()
                .transform(w -> w.as(Message.class).orElseThrow());
    }

}
