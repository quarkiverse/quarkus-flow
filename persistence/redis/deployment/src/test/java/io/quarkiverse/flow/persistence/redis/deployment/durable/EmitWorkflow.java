package io.quarkiverse.flow.persistence.redis.deployment.durable;

import static io.quarkiverse.flow.persistence.redis.deployment.DurableListenWorkflowIT.EVENT_NAME;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emit;

import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.cloudevents.jackson.JsonCloudEventData;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class EmitWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("emitWorkflow")
                .tasks(emit(EVENT_NAME, string -> {
                    ObjectNode node = JsonNodeFactory.instance.objectNode().put("message", string);
                    return JsonCloudEventData.wrap(node).toBytes();
                }, String.class))
                .build();
    }
}
