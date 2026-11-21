package io.quarkiverse.flow.deployment.test;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.testing.TestWorkflowExecutionListener;
import io.quarkiverse.flow.testing.WorkflowEventStore;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;

public class FlowTestingProcessorTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(HelloWorldWorkflow.class))
            .withConfigurationResource("application-test-random.properties");
    // quarkus-flow-testing was added as a test dependency

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
