package io.quarkiverse.flow.persistence.redis.deployment.durable;

import static io.quarkiverse.flow.persistence.redis.deployment.DurableListenWorkflowIT.EVENT_NAME;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.toOne;

import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowModel;

@ApplicationScoped
public class ListenWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("listenWorkflow")
                .tasks(
                        function("printMessage", o -> {
                            Log.info("Printing the message from previous task: " + o.asText().orElseThrow());
                            DurableResource.PRINT_MESSAGE_TIMES.incrementAndGet();
                            return o.asMap();
                        }, WorkflowModel.class),
                        listen("waitUser", toOne(EVENT_NAME)),
                        function("printDecision", o -> {
                            DurableResource.PRINT_DECISION_TIMES.incrementAndGet();
                            return "success";
                        }))
                .build();
    }
}
