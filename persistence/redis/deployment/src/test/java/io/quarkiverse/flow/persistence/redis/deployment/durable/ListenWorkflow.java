package io.quarkiverse.flow.persistence.redis.deployment.durable;

import static io.quarkiverse.flow.persistence.redis.deployment.DurableListenWorkflowTest.EVENT_NAME;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.event;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.set;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.to;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowModel;

@ApplicationScoped
public class ListenWorkflow extends Flow {

    @Override
    public io.serverlessworkflow.api.types.Workflow descriptor() {
        return FuncWorkflowBuilder.workflow()
                .tasks(
                        set(Map.of("message", "hello friend")),
                        function("printMessage", o -> {
                            Log.info("Printing the message from previous task: " + o.asText().orElseThrow());
                            return o.asMap();
                        }, WorkflowModel.class),
                        listen("waitUser", to().one(event(EVENT_NAME))),
                        function("printDecision", o -> {
                            Log.info("Printing decision: " + o.asText().orElseThrow());
                            return "success";
                        }, WorkflowModel.class))
                .build();
    }
}
