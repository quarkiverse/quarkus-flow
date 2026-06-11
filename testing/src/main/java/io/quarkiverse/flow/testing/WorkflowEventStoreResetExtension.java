package io.quarkiverse.flow.testing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

public class WorkflowEventStoreResetExtension implements QuarkusTestBeforeEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowEventStoreResetExtension.class);

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        try (InstanceHandle<WorkflowEventStore> handle = Arc.container().instance(WorkflowEventStore.class)) {
            LOG.debug("Clearing WorkflowEventStore before test method: {}", context.getTestMethod().getName());
            if (handle.isAvailable()) {
                handle.get().clear();
            }
        }
    }
}
