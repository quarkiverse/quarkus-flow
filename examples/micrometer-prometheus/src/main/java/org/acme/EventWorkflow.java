package org.acme;

import java.util.Collection;

import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consume;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.toOne;

@ApplicationScoped
public class EventWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return workflow("wait-event").tasks(
                listen("waitUser", toOne("org.acme.message.received.v1.Message"))
                        .outputAs((Collection<Object> c) -> c.iterator().next()),
                consume("printMessage", message -> Log.info("Printing message: " + message), Message.class)).build();
    }
}
