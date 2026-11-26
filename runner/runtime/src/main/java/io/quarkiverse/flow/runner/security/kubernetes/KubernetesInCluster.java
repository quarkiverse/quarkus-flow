package io.quarkiverse.flow.runner.security.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.flow.runner.FlowRunnerConfig;

/**
 * Detects whether the application is running inside a Kubernetes cluster and
 * whether the embedded Kubernetes ServiceAccount authentication mechanism
 * should be activated.
 * <p>
 * In-cluster detection follows the same conventions as the official Kubernetes
 * clients: the {@code KUBERNETES_SERVICE_HOST} environment variable must be
 * set (injected by the kubelet into every pod) and the ServiceAccount token
 * file projected at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/token} must be readable.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
 */
public final class KubernetesInCluster {

    /**
     * Environment variable injected by the kubelet into every pod, pointing to
     * the Kubernetes API server.
     */
    static final String SERVICE_HOST_ENV = "KUBERNETES_SERVICE_HOST";

    /**
     * Path of the projected ServiceAccount token inside a pod.
     */
    static final String TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    private KubernetesInCluster() {
        // Utility class
    }

    /**
     * Checks whether the application is running inside a Kubernetes cluster.
     * <p>
     * Returns {@code true} if and only if the {@code KUBERNETES_SERVICE_HOST}
     * environment variable is set and the ServiceAccount token file at
     * {@code /var/run/secrets/kubernetes.io/serviceaccount/token} is a readable
     * file.
     *
     * @return {@code true} when running inside a Kubernetes cluster
     */
    public static boolean isRunningInCluster() {
        return System.getenv(SERVICE_HOST_ENV) != null && Files.isReadable(Path.of(TOKEN_PATH));
    }

    /**
     * Checks whether the embedded Kubernetes ServiceAccount authentication
     * mechanism is active for the given configuration.
     * <p>
     * The decision is driven by
     * {@code quarkus.flow.runner.security.embed.kubernetes.enabled}:
     * <ul>
     * <li>{@code true} - always active, regardless of the environment</li>
     * <li>{@code false} - never active</li>
     * <li>{@code auto} (default) - active only when running in a Kubernetes
     * cluster</li>
     * </ul>
     *
     * @param config the Flow Runner configuration
     * @return {@code true} when the mechanism should be activated
     */
    public static boolean isMechanismActive(FlowRunnerConfig config) {
        String enabled = config.security().embed().kubernetes().enabled();
        return computeActive(enabled, isRunningInCluster());
    }

    /**
     * Computes whether the mechanism is active given the configured enabled
     * value and the in-cluster detection result.
     *
     * @param enabled the configured enabled value ({@code true}, {@code false},
     *        or {@code auto}); {@code null} or unknown values fall back to
     *        {@code auto} semantics
     * @param inCluster whether the application is running inside a Kubernetes
     *        cluster
     * @return {@code true} when the mechanism should be activated
     */
    static boolean computeActive(String enabled, boolean inCluster) {
        if ("true".equalsIgnoreCase(enabled)) {
            return true;
        }
        if ("false".equalsIgnoreCase(enabled)) {
            return false;
        }
        return inCluster;
    }
}
