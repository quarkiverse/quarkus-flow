package io.quarkiverse.flow.durable.kube;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;

@ApplicationScoped
public class Fabric8KubeInfoStrategy implements KubeInfoStrategy {

    private static final String POD_NAME_ENV_VAR = "POD_NAME";
    private static final String NAMESPACE_ENV_VAR = "POD_NAMESPACE";
    private static final String HOSTNAME_ENV_VAR = "HOSTNAME";

    private static final Path SERVICE_ACCOUNT_NAMESPACE_PATH = Path
            .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    @Inject
    KubernetesClient client;

    private volatile String cachedNamespace;
    private volatile String cachedPodName;

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

    @Override
    public String namespace() {
        String namespace = cachedNamespace;
        if (namespace != null && !namespace.isBlank()) {
            return namespace;
        }

        namespace = resolveNamespace();
        cachedNamespace = namespace;
        return namespace;
    }

    private String resolveNamespace() {
        String namespace = System.getenv(NAMESPACE_ENV_VAR);
        if (namespace != null && !namespace.isBlank()) {
            return namespace;
        }

        namespace = readNamespaceFromServiceAccount();
        if (namespace != null && !namespace.isBlank()) {
            return namespace;
        }

        namespace = client.getNamespace();
        if (namespace != null && !namespace.isBlank()) {
            return namespace;
        }

        throw new IllegalStateException(
                "Impossible to get current namespace. Please set " + NAMESPACE_ENV_VAR
                        + " on the current deployment via Downward API, ensure the service account namespace file is mounted, "
                        + "or configure the Kubernetes client namespace explicitly.");
    }

    @Override
    public String podName() {
        String podName = cachedPodName;
        if (podName != null && !podName.isBlank()) {
            return podName;
        }

        podName = resolvePodName();
        cachedPodName = podName;
        return podName;
    }

    private String resolvePodName() {
        String podName = System.getenv(POD_NAME_ENV_VAR);
        if (podName == null || podName.isBlank()) {
            podName = System.getenv(HOSTNAME_ENV_VAR);
        }

        if (podName == null || podName.isBlank()) {
            throw new IllegalStateException(
                    "Impossible to get current pod name. Please set " + POD_NAME_ENV_VAR
                            + " on the current deployment via Downward API or check if " + HOSTNAME_ENV_VAR
                            + " is set and available.");
        }

        return podName;
    }
}
