package io.quarkiverse.flow.deployment.test.http;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.post;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class HttpRestFlow extends Flow {

    @ConfigProperty(name = "org.acme.endpoint")
    String endpoint;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder
                .workflow("rest")
                .tasks(post(Map.of("hello", "${ .message }"), endpoint))
                .build();
    }
}
