package io.quarkiverse.flow.durable.kube;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.profile.IfBuildProfile;

@IfBuildProfile("dev")
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
