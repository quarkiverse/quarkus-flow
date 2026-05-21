package io.quarkiverse.flow.testing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

@ApplicationScoped
public class FlowTestingProducer {

    @Produces
    @Singleton
    public WorkflowExecutionListener testWorkflowExecutionListener(WorkflowEventStore eventStore) {
        return new TestWorkflowExecutionListener(eventStore);
    }

    @Produces
    @Singleton
    public WorkflowEventStore testWorkflowEventStore() {
        return new WorkflowEventStore();
    }
}
