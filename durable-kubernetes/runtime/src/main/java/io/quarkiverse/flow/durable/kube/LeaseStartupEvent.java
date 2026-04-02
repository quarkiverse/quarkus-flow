package io.quarkiverse.flow.durable.kube;

/**
 * Fired when the application is ready to begin acquiring Kubernetes Leases.
 */
public record LeaseStartupEvent() {
}
