package io.quarkiverse.flow.durable.kube;

import java.nio.file.Files;
import java.nio.file.Path;

public final class KubernetesAwareness {
    private KubernetesAwareness() {
    }

    static final String POD_NAME_ENV_VAR = "POD_NAME";
    static final String NAMESPACE_ENV_VAR = "POD_NAMESPACE";
    // Usually, tools add this env var to deployments - we can use it as an alternative in case users forgot to set POD_NAMESPACE
    static final String K8S_NAMESPACE_ENV_VAR = "KUBERNETES_NAMESPACE";
    static final String HOSTNAME_ENV_VAR = "HOSTNAME";
    private static final String KUBERNETES_SERVICE_HOST_ENV_VAR = "KUBERNETES_SERVICE_HOST";

    private static final Path SERVICE_ACCOUNT_NAMESPACE_PATH = Path
            .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    private static final Path SERVICE_ACCOUNT_TOKEN_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");

    private static String readNamespaceFromServiceAccount() {
        try {
            if (Files.isRegularFile(SERVICE_ACCOUNT_NAMESPACE_PATH)) {
                return Files.readString(SERVICE_ACCOUNT_NAMESPACE_PATH).trim();
            }
        } catch (Exception ignored) {
            // best-effort fallback
        }
        return null;
    }

    /**
     * Detects if the application is running inside a Kubernetes cluster.
     * Checks for the presence of service account files (most reliable) and
     * falls back to the KUBERNETES_SERVICE_HOST environment variable.
     *
     * @return true if running in Kubernetes, false otherwise
     */
    public static boolean isRunningInKubernetes() {
        // Primary: Check for service account token (always mounted by default)
        if (Files.isRegularFile(SERVICE_ACCOUNT_TOKEN_PATH)) {
            return true;
        }

        // Secondary: Check for service account namespace file
        if (Files.isRegularFile(SERVICE_ACCOUNT_NAMESPACE_PATH)) {
            return true;
        }

        // Fallback: Check environment variable (can be disabled via enableServiceLinks: false)
        return System.getenv(KUBERNETES_SERVICE_HOST_ENV_VAR) != null;
    }

    static String tryResolveCurrentNamespaceOrNull() {
        String namespace = System.getenv(NAMESPACE_ENV_VAR);
        if (namespace != null && !namespace.isBlank())
            return namespace;

        namespace = System.getenv(K8S_NAMESPACE_ENV_VAR);
        if (namespace != null && !namespace.isBlank())
            return namespace;

        namespace = readNamespaceFromServiceAccount();
        if (namespace != null && !namespace.isBlank())
            return namespace;

        return null;
    }

    static String tryResolveCurrentPodNameOrNull() {
        String podName = System.getenv(POD_NAME_ENV_VAR);
        if (podName == null || podName.isBlank()) {
            podName = System.getenv(HOSTNAME_ENV_VAR);
        }
        if (podName == null || podName.isBlank()) {
            return null;
        }
        return podName;
    }
}
