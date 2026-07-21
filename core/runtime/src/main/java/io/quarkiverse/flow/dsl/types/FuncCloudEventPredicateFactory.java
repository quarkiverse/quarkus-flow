package io.quarkiverse.flow.dsl.types;

import io.serverlessworkflow.api.types.EventProperties;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.events.CloudEventPredicate;
import io.serverlessworkflow.impl.events.CloudEventPredicateFactory;
import io.serverlessworkflow.impl.events.DefaultCloudEventPredicate;

public class FuncCloudEventPredicateFactory implements CloudEventPredicateFactory {

    @Override
    public CloudEventPredicate build(WorkflowApplication appl, EventProperties props) {
        return props instanceof EventPropertiesPredicate funcProps
                ? new FuncCloudEventPredicate(funcProps, appl)
                : new DefaultCloudEventPredicate(props, appl);
    }
}
