package io.quarkiverse.flow.deployment.test.devui;

import static io.quarkiverse.flow.dsl.FlowDSL.function;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class DevUIWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("helloQuarkus")
                .tasks(t -> t.set("taskHello", """
                        { "message": "helloWorld" }
                        """),
                        function("aliceFn", person -> Map.of("name", "Alice"), Map.class),
                        function("messageFn", person -> Map.of("message", "Message from " + person.get("name") + " to me!"),
                                Map.class))
                .build();
    }
}
