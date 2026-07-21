package io.quarkiverse.flow.persistence.test.durable;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.quarkus.logging.Log;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowModel;

@ApplicationScoped
public class ListenWorkflow extends Flow {

    public static final String EVENT_NAME = "org.acme.user.decision.Decision";

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("listenWorkflow")
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
