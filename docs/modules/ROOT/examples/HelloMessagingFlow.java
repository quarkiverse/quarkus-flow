package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.*;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HelloMessagingFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("hello-messaging")
                .tasks(
                        // Wait for one request event
                        listen("waitHello", to().one(event("org.acme.hello.request")))
                                // listen() returns a collection; pick the first
                                .outputAs((java.util.Collection<Object> c) -> c.iterator().next()),

                        // Build a response with jq
                        set("{ message: \"Hello \" + .name }"),

                        // Emit the response event
                        emitJson("org.acme.hello.response", java.util.Map.class))
                .build();
    }
}
