package io.quarkiverse.flow.durable.kube.config;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.flow.durable.kube.lease")
public interface LeaseGroupConfig {

    /**
     * Specific pool member configuration.
     */
    LeaseConfig member();

    /**
     * Specific pool leader configuration.
     */
    LeaseConfig leader();

    interface LeaseConfig {
        /**
         * Duration, in seconds, for the Lease object to wait to renew the lock
         */
        @WithDefault("30")
        Integer duration();

        /**
         * Whether to remove this node from trying to renew the lease
         */
        @WithDefault("true")
        Boolean enabled();

        /**
         * Maximum time to wait for a lease to be acquired during startup.
         */
        @WithDefault("30s")
        Duration acquireTimeout();

        /**
         * Maximum number of attempts to re-acquire the bound lease after it is lost.
         * Once exhausted, the pod initiates a graceful shutdown to avoid split-brain
         * (the applicationId is immutable and cannot match a different lease).
         */
        @WithDefault("5")
        Integer maxReacquireAttempts();
    }

}
