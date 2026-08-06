package io.quarkiverse.flow.messaging.it;

import static io.quarkiverse.flow.dsl.FlowDSL.emit;
import static io.quarkiverse.flow.dsl.FlowDSL.listen;
import static io.quarkiverse.flow.dsl.FlowDSL.produced;
import static io.quarkiverse.flow.dsl.FlowDSL.set;
import static io.quarkiverse.flow.dsl.FlowDSL.toOne;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;

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
     * @see <a href="https://github.com/open-workflow-specification/specification/blob/main/dsl-reference.md#listen">DSL Reference:
     *      Listen</a>
     * @see <a href="https://github.com/open-workflow-specification/specification/blob/main/dsl-reference.md#emit">DSL Reference:
     *      Emit</a>
     */
    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("hello-messaging-flow")
                // We are listening to one and only one event coming to our broker with the type "io.quarkiverse.flow.messaging.hello.request"
                // Each event produced by the broker with this type will kick a new workflow instance.
                // To learn more see the base specification: https://github.com/open-workflow-specification/specification/blob/main/dsl-reference.md#listen
                .tasks(listen(toOne("io.quarkiverse.flow.messaging.hello.request").first()),
                        // "name" is expected in the message body payload
                        set("{ message: \"Hello \" + .name + \"!\" }"),
                        // We emit a new event with the specified type having the property `message` in the body that we built in the previous `set` task.
                        emit(produced("io.quarkiverse.flow.messaging.hello.response").jsonData(Map.class)))
                .build();
    }
}
