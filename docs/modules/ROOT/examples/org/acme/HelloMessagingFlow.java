package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class HelloMessagingFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("hello-messaging")
                .tasks(
                        listen("waitHello", toOne("org.acme.hello.request").first()),

                        // Build a response with jq
                        set("{ message: \"Hello \" + .name }"),

                        // Emit the response event
                        emitJson("org.acme.hello.response", Map.class))
                .build();
    }
}
