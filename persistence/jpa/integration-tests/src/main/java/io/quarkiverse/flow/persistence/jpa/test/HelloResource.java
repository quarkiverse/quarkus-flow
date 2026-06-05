package io.quarkiverse.flow.persistence.jpa.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/hello-flow")
@ApplicationScoped
public class HelloResource {

    @Inject
    HelloWorkflow hello;

    @Inject
    EntityManager em;

    @POST
    @Transactional
    public Response hello() {
        em.persist(new Alert("slack,high-priority,critical"));
        return Response.status(Response.Status.OK)
                .entity(hello.startInstance().await().indefinitely()).build();
    }

    @POST
    @Path("async")
    @Transactional
    public Response helloAsync() {
        em.persist(new Alert("slack,high-priority,critical"));
        hello.startInstance();
        return Response.status(Response.Status.ACCEPTED).build();
    }

}
