package io.quarkiverse.flow.deployment.test;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.testing.TestWorkflowExecutionListener;
import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkus.test.QuarkusExtensionTest;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

public class FlowTestingProcessorTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            // quarkus-flow-testing was added as a test dependency
            .withEmptyApplication();

    @Inject
    WorkflowEventStore listener;

    @Inject
    Instance<WorkflowExecutionListener> listeners;

    @Test
    void should_inject_test_workflow_execution_listener() {
        Assertions.assertNotNull(listener);
        boolean isAvailable = listeners.stream()
                .anyMatch(listener -> listener instanceof TestWorkflowExecutionListener);
        Assertions.assertTrue(isAvailable);
    }

}
