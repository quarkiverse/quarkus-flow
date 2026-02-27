package io.quarkiverse.flow.durable.kube;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.flow.durable.kube")
public interface FlowDurableKubeConfig {

    @ConfigGroup
    interface Health {

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
