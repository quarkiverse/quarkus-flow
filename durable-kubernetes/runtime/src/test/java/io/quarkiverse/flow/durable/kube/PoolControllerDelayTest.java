package io.quarkiverse.flow.durable.kube;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.enterprise.inject.Vetoed;

import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.durable.kube.config.LeaseGroupConfig;
import io.quarkiverse.flow.durable.kube.config.SchedulerGroupConfig;

/**
 * Tests for {@link PoolController#computeSchedulerDelay()}.
 */
class PoolControllerDelayTest {

    @Vetoed
    static class TestablePoolController extends PoolController {

        private final SchedulerGroupConfig.SchedulerConfig schedulerConfig;

        TestablePoolController(SchedulerGroupConfig.SchedulerConfig schedulerConfig) {
            this.schedulerConfig = schedulerConfig;
        }

        @Override
        protected String scheduledExecutorName() {
            return "test";
        }

        @Override
        protected String leaseName() {
            return null;
        }

        @Override
        protected SchedulerGroupConfig.SchedulerConfig schedulerConfig() {
            return schedulerConfig;
        }

        @Override
        protected LeaseGroupConfig.LeaseConfig leaseConfig() {
            return null;
        }

        @Override
        public void run() {
        }
    }

    private static SchedulerGroupConfig.SchedulerConfig configWithDelay(String delay) {
        return new SchedulerGroupConfig.SchedulerConfig() {
            @Override
            public String interval() {
                return "30s";
            }

            @Override
            public String initialDelay() {
                return delay;
            }
        };
    }

    @Test
    void computeSchedulerDelay_numeric_input_adds_suffix() {
        TestablePoolController controller = new TestablePoolController(configWithDelay("10"));

        String delay = controller.computeSchedulerDelay();

        assertEquals("10s", delay);
    }

    @Test
    void computeSchedulerDelay_suffixed_input_preserves_suffix() {
        TestablePoolController controller = new TestablePoolController(configWithDelay("10s"));

        String delay = controller.computeSchedulerDelay();

        assertEquals("10s", delay);
    }
}