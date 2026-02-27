package io.quarkiverse.flow.durable.kube;

import static io.quarkiverse.flow.durable.kube.KubeUtils.ownerName;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.arc.DefaultBean;

@DefaultBean
@ApplicationScoped
public class DeploymentPoolTopologyResolver implements PoolTopologyResolver {

    private static final String REPLICASET_KIND = "ReplicaSet";
    private static final String DEPLOYMENT_KIND = "Deployment";
    private static final String DEPLOYMENT_API_VERSION = "apps/v1";

    private static final Logger LOG = LoggerFactory.getLogger(DeploymentPoolTopologyResolver.class);
    @Inject
    KubernetesClient client;
    @Inject
    KubeInfoStrategy kubeInfo;

    private volatile String cachedDeploymentName;

    @Override
    public Optional<Integer> desiredReplicas() {
        return resolveCurrentDeployment().map(d -> d.getSpec().getReplicas());
    }

    @Override
    public List<OwnerReference> leaseOwnerReferences() {
        return resolveCurrentDeployment().map(d -> List.of(new OwnerReferenceBuilder()
                .withName(d.getMetadata().getName())
                .withUid(d.getMetadata().getUid())
                .withController(false)
                .withKind(DEPLOYMENT_KIND)
                .withApiVersion(DEPLOYMENT_API_VERSION)
                .build()))
                .orElse(List.of());
    }

    /**
     * Attempts to resolve the current Deployment controlling this Pod.
     * Returns empty if the Pod is not controlled by a Deployment (or if RBAC/API prevents resolving).
     */
    private Optional<Deployment> resolveCurrentDeployment() {
        final String ns = kubeInfo.namespace();

        if (cachedDeploymentName != null) {
            Deployment d = client.apps().deployments().inNamespace(ns).withName(cachedDeploymentName).get();
            if (d != null) {
                return Optional.of(d);
            }
            // stale cache (or no access); clear and retry resolution
            cachedDeploymentName = null;
        }

        Pod pod = client.pods().inNamespace(ns).withName(kubeInfo.podName()).get();
        if (pod == null) {
            LOG.warn("Failed to find pod {} in namespace {}", kubeInfo.podName(), ns);
            return Optional.empty();
        }

        Optional<String> rsName = ownerName(pod, REPLICASET_KIND);
        if (rsName.isEmpty()) {
            LOG.warn("Failed to find replica for pod {} in namespace {}", pod.getMetadata().getName(), ns);
            return Optional.empty();
        }

        ReplicaSet rs = client.apps().replicaSets().inNamespace(ns).withName(rsName.get()).get();
        if (rs == null) {
            LOG.warn("Failed to find replica set {} in namespace {}", rsName.get(), ns);
            return Optional.empty();
        }

        Optional<String> deploymentName = ownerName(rs, DEPLOYMENT_KIND);
        if (deploymentName.isEmpty()) {
            LOG.warn("Failed to find deployment name for replica set {} in namespace {}", rsName.get(), ns);
            return Optional.empty();
        }

        Deployment d = client.apps().deployments().inNamespace(ns).withName(deploymentName.get()).get();
        if (d == null) {
            LOG.warn("Failed to find deployment {} in namespace {}", deploymentName.get(), ns);
            return Optional.empty();
        }

        cachedDeploymentName = deploymentName.get();
        return Optional.of(d);
    }
}
