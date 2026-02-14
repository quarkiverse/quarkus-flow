package io.quarkiverse.flow.durable.kube;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.flow.durable.kube.schedulers")
public interface SchedulerGroupConfig {
    String SCHEDULER_INITIAL_DELAY_DEFAULT = "random";

    /**
     * Pool Leader Controller configuration. Configure specific parameters for the reconciliation loop.
     */
    SchedulerConfig leader();

    /**
     * Pool Member Controller configuration. Configure specific parameters for the internal lease renew scheduler.
     */
    SchedulerConfig member();

    interface SchedulerConfig {
        /**
         * Interval of the controller reconcile cycle set on ISO-8601 format.
         * This interval marks how much time the internal controller must run.
         *
         * @see <a href="https://quarkus.io/guides/scheduler-reference#intervals">Quarkus Scheduler Guide</a>
         */
        @WithDefault("30s")
        String interval();

        /**
         * Unless strictly necessary, don't set this property. It governs the initial time in seconds for the scheduler to
         * start running.
         * It defaults to "random" to avoid pilling up API requests to the Kubernetes server.
         */
        @WithDefault(SCHEDULER_INITIAL_DELAY_DEFAULT)
        String initialDelay();
    }
}
