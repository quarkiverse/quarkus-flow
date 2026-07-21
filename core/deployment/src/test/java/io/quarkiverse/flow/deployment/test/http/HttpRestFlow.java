package io.quarkiverse.flow.deployment.test.http;

import static io.quarkiverse.flow.dsl.FlowDSL.post;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class HttpRestFlow extends Flow {

    @ConfigProperty(name = "org.acme.endpoint")
    String endpoint;

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder
                .workflow("rest")
                .tasks(post(Map.of("hello", "${ .message }"), endpoint))
                .build();
    }
}
