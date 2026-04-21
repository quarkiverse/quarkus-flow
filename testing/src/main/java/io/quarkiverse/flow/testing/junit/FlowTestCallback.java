package io.quarkiverse.flow.testing.junit;

import io.quarkus.test.junit.callback.QuarkusTestAfterAllCallback;
import io.quarkus.test.junit.callback.QuarkusTestAfterConstructCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeTestExecutionCallback;
import io.quarkus.test.junit.callback.QuarkusTestContext;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

public class FlowTestCallback
        implements QuarkusTestAfterConstructCallback, QuarkusTestAfterAllCallback, QuarkusTestBeforeTestExecutionCallback {

    private static final FlowManager INSTANCE = new FlowManager();

    @Override
    public void afterAll(QuarkusTestContext context) {
        INSTANCE.afterAll(context);
    }

    @Override
    public void afterConstruct(Object testInstance) {
        INSTANCE.afterConstruct(testInstance);
    }

    @Override
    public void beforeTestExecution(QuarkusTestMethodContext context) {
        INSTANCE.beforeTestExecution(context);
    }

    private static class FlowManager
            implements QuarkusTestAfterConstructCallback, QuarkusTestAfterAllCallback, QuarkusTestBeforeTestExecutionCallback {

        @Override
        public void afterAll(QuarkusTestContext context) {

        }

        @Override
        public void afterConstruct(Object testInstance) {

        }

        @Override
        public void beforeTestExecution(QuarkusTestMethodContext context) {

        }
    }
}
