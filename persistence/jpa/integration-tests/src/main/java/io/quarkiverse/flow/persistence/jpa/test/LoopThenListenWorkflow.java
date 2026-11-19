package io.quarkiverse.flow.persistence.jpa.test;

import static io.quarkiverse.flow.dsl.FlowDSL.function;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.switchWhenOrElse;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

@ApplicationScoped
public class LoopThenListenWorkflow extends Flow {

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("loopThenListen")
                .tasks(
                        function("increment", (Integer n) -> n + 1, Integer.class),
                        switchWhenOrElse("again", (Integer n) -> n < 3, "increment", "waitForever", Integer.class),
                        listen("waitForever", toOne("org.acme.never")))
                .build();
    }

}
