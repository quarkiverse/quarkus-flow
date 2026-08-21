package io.quarkiverse.flow.testing;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.test.junit.callback.QuarkusTestAfterAllCallback;
import io.quarkus.test.junit.callback.QuarkusTestAfterConstructCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestContext;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

public class QuarkusFlowTestCallback
        implements QuarkusTestAfterConstructCallback, QuarkusTestBeforeTestExecutionCallback, QuarkusTestAfterAllCallback {

    private static final Logger log = Logger.getLogger(QuarkusFlowTestCallback.class);

    /**
     * Test classes for which a {@code PER_CLASS} recording session has already been started.
     * {@code afterConstruct} fires once per test instance (i.e. once per method under the
     * default JUnit lifecycle), so this guards the class-wide session against being restarted
     * on every method.
     */
    private static final Set<Class<?>> classScopeStarted = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConstruct(Object testInstance) {
        Class<?> testClass = testInstance.getClass();
        QuarkusFlowTest classAnnotation = testClass.getAnnotation(QuarkusFlowTest.class);
        if (classAnnotation == null || classAnnotation.scope() != QuarkusFlowTest.Scope.PER_CLASS) {
            return;
        }

        if (classScopeStarted.add(testClass)) {
            log.debugv("Starting PER_CLASS recording session for: {0}", testClass.getName());
            clearStore();
        }
    }

    @Override
    public void beforeTestExecution(QuarkusTestMethodContext context) {
        Method testMethod = context.getTestMethod();
        Class<?> testClass = context.getTestInstance().getClass();

        QuarkusFlowTest method = testMethod.getAnnotation(QuarkusFlowTest.class);
        QuarkusFlowTest clazz = testClass.getAnnotation(QuarkusFlowTest.class);
        QuarkusFlowTest effective = method != null ? method : clazz;

        if (effective == null || effective.scope() != QuarkusFlowTest.Scope.PER_METHOD) {
            return;
        }

        log.debugv("Starting PER_METHOD recording session for: {0}#{1}", testClass.getName(), testMethod.getName());
        clearStore();
    }

    @Override
    public void afterAll(QuarkusTestContext context) {

        Class<?> testClass = context.getTestInstance().getClass();
        if (classScopeStarted.remove(testClass)) {
            log.debugv("Ending PER_CLASS recording session for: {0}", testClass.getName());
            clearStore();
        }
    }

    private void clearStore() {
        try (InstanceHandle<WorkflowEventStore> handle = Arc.container().instance(WorkflowEventStore.class)) {
            if (handle.isAvailable()) {
                handle.get().clear();
            }
        }
    }
}
