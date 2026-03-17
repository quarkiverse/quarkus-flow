package org.acme;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HelloWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("hello")
                // setting the workflow context with a map carrying a message.
                // it can be translated to JSON as { "message": "hello world!" }
                .tasks(set(Map.of("message", "hello world!")))
                .build();
    }
}
