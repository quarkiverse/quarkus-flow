package io.quarkiverse.flow.durable.kube;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.flow.durable.kube.pool")
public interface PoolConfig {

    /**
     * It's highly recommended that users set this property to not have objects clashing on Kubernetes.
     * <p/>
     * The group pool name used to create the Lease objects coordination on the cluster.
     * It's used to name and label every object created by the pool.
     */
    @WithDefault("flow-pool")
    String name();

}
