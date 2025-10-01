package io.quarkiverse.flow.messaging.it;

import static io.serverlessworkflow.fluent.spec.dsl.DSL.event;
import static io.serverlessworkflow.fluent.spec.dsl.DSL.to;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.spec.WorkflowBuilder;

@ApplicationScoped
public class HelloMessagingFlow extends Flow {
    /**
     * This example illustrates how we consume a CloudEvent filtering it by "type".<br/>
     * By the end of the workflow, we emit the event adding to the payload the attribute `message` we built via JQ in the `set`
     * task.
     * <p/>
     * The infrastructure burden is handled internally by Kafka/SmallRye/Workflow runtime.<br/>
     * As you may notice, this workflow is not tied to an event infrastructure whatsoever. Any connector supported by SmallRye
     * would work just fine.
     * <p/>
     * To know more about the infrastructure configuration, please see the application.properties and the tests in this module.
     *
     * @see <a href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#listen">DSL Reference:
     *      Listen</a>
     * @see <a href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#emit">DSL Reference:
     *      Emit</a>
     */
    @Override
    public Workflow descriptor() {
        return WorkflowBuilder.workflow()
                // We are listening to one and only one event coming to our broker with the type "io.quarkiverse.flow.messaging.hello.request"
                // Each event produced by the broker with this type will kick a new workflow instance.
                // To learn more see the base specification: https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#listen
                .tasks(t -> t.listen(to().one(e -> e.type("io.quarkiverse.flow.messaging.hello.request")))
                        // "name" is expected in the message body payload
                        // by design, we receive an array from the listen task, since it's only one we are expecting it's safe to index
                        // on more a more robust scenario, you should use `forEach`.
                        .set("{ message: \"Hello \" + .[0].name }")
                        // We emit a new event with the specified type having the property `message` in the body that we built in the previous `set` task.
                        .emit(e -> e
                                .event(event().type("io.quarkiverse.flow.messaging.hello.response").jsonData("{ message }"))))
                .build();
    }
}
