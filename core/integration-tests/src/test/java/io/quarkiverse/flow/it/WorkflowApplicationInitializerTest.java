package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowApplicationInitializer;
import io.quarkiverse.flow.internal.WorkflowApplicationReadyEvent;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class WorkflowApplicationInitializerTest {

    @Inject
    WorkflowApplicationInitializer initializer;

    @Inject
    ReadyEventObserver observer;

    @Test
    @DisplayName("appInfo is only marked ready after every WorkflowApplicationReadyEvent listener has completed")
    public void appinfo_is_updated_only_after_ready_event_listeners_complete() {
        assertThat(observer.wasObserved())
                .as("WorkflowApplicationReadyEvent should have been fired and observed during startup")
                .isTrue();
        assertThat(observer.wasAppInfoReadyDuringEvent())
                .as("appInfo must still report not-ready while ready-event listeners are still running")
                .isFalse();

        assertThat(initializer.getAppInfo().ready())
                .as("appInfo must be ready once fire() returns, i.e. after all listeners finished")
                .isTrue();
        assertThat(initializer.getAppInfo().id())
                .isEqualTo(observer.getObservedApplicationId());
    }

    @ApplicationScoped
    public static class ReadyEventObserver {

        static final Logger LOG = LoggerFactory.getLogger(ReadyEventObserver.class);

        @Inject
        WorkflowApplicationInitializer initializer;

        private volatile boolean observed;
        private volatile boolean appInfoReadyDuringEvent;
        private volatile String observedApplicationId;

        void onReady(@Observes WorkflowApplicationReadyEvent event) {
            observed = true;
            appInfoReadyDuringEvent = initializer.getAppInfo().ready();
            observedApplicationId = event.id();
        }

        boolean wasObserved() {
            return observed;
        }

        boolean wasAppInfoReadyDuringEvent() {
            return appInfoReadyDuringEvent;
        }

        String getObservedApplicationId() {
            return observedApplicationId;
        }
    }
}
