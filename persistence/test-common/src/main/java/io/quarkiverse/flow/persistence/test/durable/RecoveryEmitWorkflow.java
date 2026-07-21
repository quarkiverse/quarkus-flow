package io.quarkiverse.flow.persistence.test.durable;

import static io.quarkiverse.flow.dsl.FlowDSL.emit;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.cloudevents.jackson.JsonCloudEventData;
import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class RecoveryEmitWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("recoveryEmitWorkflow")
                .tasks(emit(RecoveryWorkflow.EVENT_NAME,
                        (String string) -> JsonCloudEventData
                                .wrap(JsonNodeFactory.instance.objectNode().put("message", string))))
                .build();
    }
}
