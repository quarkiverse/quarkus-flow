package io.quarkiverse.flow.runner.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;
import io.serverlessworkflow.fluent.spec.dsl.DSL;

/**
 * Long-running workflow that sleeps for 5 seconds.
 * Used to test async execution where wait=false should return RUNNING status immediately.
 */
@ApplicationScoped
public class LongRunningFlow extends Flow {

    @Override
    public Workflow descriptor() {
        return WorkflowBuilder.workflow("long-running", "test-namespace", "1.0.0")
                .tasks(
                        DSL.set("${ { \"start\": now } }"),
                        DSL.wait("waitTask", t -> t.duration(d -> d.seconds(5))),
                        DSL.set("${ . + { \"end\": now } }"))
                .build();
    }
}
