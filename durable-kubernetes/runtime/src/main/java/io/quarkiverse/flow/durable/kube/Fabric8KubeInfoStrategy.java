package io.quarkiverse.flow.durable.kube;

import static io.quarkiverse.flow.durable.kube.KubernetesAwareness.HOSTNAME_ENV_VAR;
import static io.quarkiverse.flow.durable.kube.KubernetesAwareness.NAMESPACE_ENV_VAR;
import static io.quarkiverse.flow.durable.kube.KubernetesAwareness.POD_NAME_ENV_VAR;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.arc.DefaultBean;

@DefaultBean
@ApplicationScoped
public class Fabric8KubeInfoStrategy implements KubeInfoStrategy {

    @Inject
    KubernetesClient client;

    volatile String cachedNamespace;
    volatile String cachedPodName;

    @Override
    public String namespace() {
        String ns = cachedNamespace;
        if (ns != null && !ns.isBlank())
            return ns;

        ns = resolveNamespaceOrNull();
        if (ns == null) {
            throw new IllegalStateException("Impossible to get current namespace. Please set " + NAMESPACE_ENV_VAR
                    + " on the current deployment via Downward API, ensure the service account namespace file is mounted, "
                    + "or configure the Kubernetes client namespace explicitly.");
        }
        cachedNamespace = ns;
        return ns;
    }

    @Override
    public String podName() {
        String p = cachedPodName;
        if (p != null && !p.isBlank())
            return p;

        p = KubernetesAwareness.tryResolveCurrentPodNameOrNull();
        if (p == null) {
            throw new IllegalStateException("Impossible to get current pod name. Please set " + POD_NAME_ENV_VAR
                    + " on the current deployment via Downward API or check if " + HOSTNAME_ENV_VAR
                    + " is set and available.");
        }
        cachedPodName = p;
        return p;
    }

    private String resolveNamespaceOrNull() {
        String namespace = KubernetesAwareness.tryResolveCurrentNamespaceOrNull();

        namespace = client.getNamespace();
        if (namespace != null && !namespace.isBlank())
            return namespace;

        return null;
    }
}
