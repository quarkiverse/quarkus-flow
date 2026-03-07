package io.quarkiverse.flow.deployment.test.devui;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class DevUIWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("helloQuarkus")
                .tasks(t -> t.set("taskHello", """
                        { "message": "helloWorld" }
                        """),
                        function("aliceFn", person -> Map.of("name", "Alice"), Map.class),
                        function("messageFn", person -> Map.of("message", "Message from " + person.get("name") + " to me!"),
                                Map.class))
                .build();
    }
}
