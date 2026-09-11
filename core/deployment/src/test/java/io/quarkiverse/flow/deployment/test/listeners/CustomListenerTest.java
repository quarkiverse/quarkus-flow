package io.quarkiverse.flow.deployment.test.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;

public class CustomListenerTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CustomExecutionListener.class))
            .withConfigurationResource("application-test-random.properties");

    @Inject
    WorkflowApplication workflowApp;

    @Test
    void should_register_custom_listener_without_duplicates() {
        assertNotNull(workflowApp, "WorkflowApplication bean should be available in the container");

        Collection<WorkflowExecutionCompletableListener> registeredListeners = workflowApp.listeners();

        boolean customFound = registeredListeners.stream()
                .anyMatch(l -> l instanceof CustomExecutionListener);
        assertTrue(customFound, "CustomExecutionListener should be registered in the WorkflowApplication");

        long customCount = registeredListeners.stream()
                .filter(l -> l instanceof CustomExecutionListener)
                .count();
        assertEquals(1, customCount, "CustomExecutionListener should be registered exactly once");

        long internalTraceCount = registeredListeners.stream()
                .filter(l -> l.getClass().getSimpleName().equals("TraceLoggerExecutionListener"))
                .count();
        assertTrue(internalTraceCount <= 1, "Internal Trace listener should not be duplicated");
    }
}
