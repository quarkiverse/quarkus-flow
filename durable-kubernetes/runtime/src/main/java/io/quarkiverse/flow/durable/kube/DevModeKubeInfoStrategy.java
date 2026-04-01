package io.quarkiverse.flow.durable.kube;

import static io.quarkiverse.flow.durable.kube.config.DevModeConfig.DEV_MODE_ENABLED_CONFIG;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.properties.IfBuildProperty;

@IfBuildProperty(name = DEV_MODE_ENABLED_CONFIG, stringValue = "true", enableIfMissing = true)
@ApplicationScoped
public class DevModeKubeInfoStrategy extends Fabric8KubeInfoStrategy {

    private final String devPod = "dev-" + ProcessHandle.current().pid();

    @Override
    public String podName() {
        String p = cachedPodName;
        if (p != null && !p.isBlank())
            return p;

        p = resolvePodNameOrNull();
        if (p == null) {
            p = devPod;
        }
        cachedPodName = p;
        return p;
    }

    @Override
    public String namespace() {
        String ns = cachedNamespace;
        if (ns != null && !ns.isBlank())
            return ns;

        ns = resolveNamespaceOrNull();
        if (ns == null) {
            ns = "default";
        }
        cachedNamespace = ns;
        return ns;
    }
}
