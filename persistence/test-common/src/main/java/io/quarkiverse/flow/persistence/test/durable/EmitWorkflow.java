package io.quarkiverse.flow.persistence.test.durable;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.emit;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.cloudevents.jackson.JsonCloudEventData;
import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;

@ApplicationScoped
public class EmitWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("emitWorkflow")
                .tasks(emit(ListenWorkflow.EVENT_NAME,
                        (String string) -> JsonCloudEventData
                                .wrap(JsonNodeFactory.instance.objectNode().put("message", string))))
                .build();
    }
}
