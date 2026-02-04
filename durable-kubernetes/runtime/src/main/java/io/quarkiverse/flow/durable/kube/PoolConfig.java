package io.quarkiverse.flow.durable.kube;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.flow.durable.kube.pool")
public interface PoolConfig {

    /**
     * Specific pool member configuration.
     */
    PoolMemberLeaseConfig members();

    /**
     * Specific pool leader configuration.
     */
    PoolLeaderLeaseConfig leader();

    interface LeaseConfig {
        /**
         * Duration, in seconds, for the Lease object to wait to renew the lock
         */
        @WithDefault("30")
        Integer leaseDuration();
    }

    interface PoolMemberLeaseConfig extends LeaseConfig {

    }

    interface PoolLeaderLeaseConfig extends LeaseConfig {
        /**
         * Whether to remove this application from trying to become a pool leader
         */
        @WithDefault("true")
        Boolean leaseEnabled();
    }

}