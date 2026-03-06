package io.quarkiverse.flow.persistence.redis.deployment.durable;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emitJson;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class EmitWorkflow extends Flow {

    @Override
    public io.serverlessworkflow.api.types.Workflow descriptor() {
        return FuncWorkflowBuilder.workflow()
                .tasks(emitJson("emitDecision", String.class))
                .build();
    }
}
