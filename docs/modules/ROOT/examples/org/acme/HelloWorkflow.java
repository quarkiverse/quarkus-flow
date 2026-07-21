package org.acme;

import static io.quarkiverse.flow.dsl.FlowDSL.set;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class HelloWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("hello")
                // setting the workflow context with a map carrying a message.
                // it can be translated to JSON as { "message": "hello world!" }
                .tasks(set(Map.of("message", "hello world!")))
                .build();
    }
}
