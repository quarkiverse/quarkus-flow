package io.quarkiverse.flow.durable.kube;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FlowDurableKubeSettings {

    private final PoolConfig pool;
    private final ControllersConfig controllers;

    @Inject
    public FlowDurableKubeSettings(PoolConfig pool,
            ControllersConfig controllers) {
        this.pool = pool;
        this.controllers = controllers;
    }

    /**
     * Runtime configuration for the application pool.
     * An application pool is a group of pods running the same deployment.
     */
    public PoolConfig pool() {
        return pool;
    }

    /**
     * Build time configuration for the controllers' configuration.
     * Controllers are internal services that interact with the Kubernetes API to create objects to support the application pool
     * coordination.
     */
    public ControllersConfig controllers() {
        return controllers;
    }
}
