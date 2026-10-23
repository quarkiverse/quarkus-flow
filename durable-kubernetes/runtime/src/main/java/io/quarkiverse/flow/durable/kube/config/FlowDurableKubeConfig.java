package io.quarkiverse.flow.durable.kube.config;

import static io.quarkiverse.flow.config.FlowRuntimeConfig.FLOW_ROOT_PREFIX;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = FlowDurableKubeConfig.DURABLE_KUBE_PREFIX)
public interface FlowDurableKubeConfig {

    String DURABLE_KUBE_PREFIX = FLOW_ROOT_PREFIX + ".durable.kube";

    Health health();

    /**
     * Scheduler configuration for the pool leader/member controllers.
     */
    SchedulerGroupConfig schedulers();

    /**
     * Lease configuration for the pool leader/member controllers.
     */
    LeaseGroupConfig lease();

    @ConfigGroup
    interface Health {

        Readiness readiness();

        @ConfigGroup
        interface Readiness {

            /**
             * Whether to require the readiness probe to return UP only if a lease is acquired.
             */
            @WithDefault("true")
            boolean requireLease();

        }

    }
}
