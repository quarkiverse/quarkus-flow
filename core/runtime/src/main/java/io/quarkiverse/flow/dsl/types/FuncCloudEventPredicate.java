package io.quarkiverse.flow.dsl.types;

import io.cloudevents.CloudEvent;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContext;
import io.serverlessworkflow.impl.WorkflowModelFactory;
import io.serverlessworkflow.impl.WorkflowPredicate;
import io.serverlessworkflow.impl.events.CloudEventAttrPredicate;
import io.serverlessworkflow.impl.events.DefaultCloudEventPredicate;
import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;

public class FuncCloudEventPredicate extends DefaultCloudEventPredicate {

    private final CloudEventAttrPredicate<CloudEvent> envelopePredicate;

    public FuncCloudEventPredicate(EventPropertiesPredicate properties, WorkflowApplication app) {
        super(properties, app);
        Object envelopePredObj = properties.getFilterPredicate();
        this.envelopePredicate = envelopePredObj == null
                ? isTrue()
                : fromCloudEvent(
                        app.modelFactory(),
                        app.expressionFactory()
                                .buildPredicate(new ExpressionDescriptor(null, envelopePredObj)));
    }

    private CloudEventAttrPredicate<CloudEvent> fromCloudEvent(
            WorkflowModelFactory workflowModelFactory, WorkflowPredicate filter) {
        return (e, w, t) -> filter.test(w, t, workflowModelFactory.from(e));
    }

    @Override
    public boolean test(CloudEvent event, WorkflowContext workflow, TaskContext task) {
        return envelopePredicate.test(event, workflow, task) && super.test(event, workflow, task);
    }
}
