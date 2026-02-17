package org.acme.flow.durable.kube;

import java.util.Map;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;

@ApplicationScoped
public class DemoWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("lease-demo")
                .tasks(
                        set(Map.of("message", "Hello from Quarkus Flow")),
                        set(". + { step2: \"second task\" }"),
                        set(". + { step3: \"third task\" }"))
                .build();
    }
}