package io.quarkiverse.flow.durable.kube.it;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.quarkiverse.flow.durable.kube.KubeInfoStrategy;

/**
 * Replaces our default Fabric8 strategy since the tests won't run in the cluster and nor POD_NAME or HOSTNAME variables will be
 * set.
 * Also, the namespace name comes from the pod file system configuration, which we don't have locally.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FixedKubeInfoStrategy implements KubeInfoStrategy {

    @Override
    public String namespace() {
        return "default";
    }

    @Override
    public String podName() {
        return "it-pod-0";
    }
}
