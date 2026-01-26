package org.acme;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consume;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.event;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.listen;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.to;

import io.quarkiverse.flow.Flow;
import io.quarkus.logging.Log;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

@ApplicationScoped
public class EventWorkflow extends Flow {
    @Override
    public Workflow descriptor() {
        return workflow("wait-event").tasks(
                listen("waitUser", to().one(event("org.acme.message.received.v1.Message")))
                        .outputAs((Collection<Object> c) -> c.iterator().next()),
                consume("printMessage", message -> Log.info("Printing message: " + message), Message.class)).build();
    }
}
