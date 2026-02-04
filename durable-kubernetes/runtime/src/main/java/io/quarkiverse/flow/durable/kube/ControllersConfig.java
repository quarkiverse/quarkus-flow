package io.quarkiverse.flow.durable.kube;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.flow.durable.kube.controllers")
public interface ControllersConfig {
    String SCHEDULER_INITIAL_DELAY_DEFAULT = "random";

    /**
     * The required group name used to create the Lease objects coordination on the cluster.
     * It's used to name and label every object created by the pool.
     */
    String poolName();

    /**
     * Pool Leader Controller configuration. Configure specific parameters for the reconciliation loop.
     */
    LeaderConfig leader();

    interface LeaderConfig {
        /**
         * Interval of the pool leader reconcile cycle set on ISO-8601 format.
         * This interval marks how much time the internal leader controller must reconcile the lease objects.
         *
         * @see <a href="https://quarkus.io/guides/scheduler-reference#intervals">Quarkus Scheduler Guide</a>
         */
        @WithDefault("30s")
        String schedulerInterval();

        /**
         * Unless strictly necessary, don't set this property. It governs the initial time in seconds for the scheduler to
         * start
         * running the internal reconciler.
         * It defaults to "random" to avoid pilling up API requests to the Kubernetes server.
         */
        @WithDefault(SCHEDULER_INITIAL_DELAY_DEFAULT)
        String schedulerInitialDelay();
    }
}
