package io.quarkiverse.flow.durable.kube.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
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
