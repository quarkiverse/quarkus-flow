package io.quarkiverse.flow.durable.kube;

import static io.quarkiverse.flow.durable.kube.KubeUtils.mapsEqual;
import static io.quarkiverse.flow.durable.kube.KubeUtils.mergeMaps;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

/**
 * Manages the creation of Kubernetes Lease objects to support the workers pool.
 *
 * @see <a href="https://kubernetes.io/docs/reference/kubernetes-api/cluster-resources/lease-v1/">Kubernetes Lease API</a>
 */
@ApplicationScoped
public class LeaseService {

    public static final String POOL_IS_LEADER_KEY = "io.quarkiverse.flow.durable.k8s/is-leader";
    public static final String POOL_NAME_LABEL_KEY = "io.quarkiverse.flow.durable.k8s/pool";
    private static final Logger LOG = LoggerFactory.getLogger(LeaseService.class);

    private static final Map<String, String> BASE_LABELS = Map.of(
            "app.kubernetes.io/managed-by", "quarkus-flow",
            "app.kubernetes.io/component", "durable");

    @Inject
    KubernetesClient client;

    @Inject
    KubeInfoStrategy kubeInfo;

    @Inject
    LeaseGroupConfig leaseConfig;

    @Inject
    PoolConfig poolConfig;

    @Inject
    PoolTopologyResolver poolTopologyResolver;

    public Optional<Lease> createOrUpdateMemberLease(String name) {
        return createOrUpdateLease(name, Map.of(POOL_IS_LEADER_KEY, "false"), leaseConfig.member().duration());
    }

    private Optional<Lease> createOrUpdateLeaderLease(String name) {
        return createOrUpdateLease(name, Map.of(POOL_IS_LEADER_KEY, "true"), leaseConfig.leader().duration());
    }

    /**
     * Create or update a given Lease in the current namespace.
     * Guarantees that all labels and LeaseDurationInSeconds are set accordingly to the configuration.
     */
    private Optional<Lease> createOrUpdateLease(String name, Map<String, String> labels, Integer leaseDuration) {
        final String ns = kubeInfo.namespace();

        LOG.debug("Attempting to create lease {} in namespace {}", name, ns);

        Lease existing = client.leases().inNamespace(ns).withName(name).get();
        labels = this.addDefaultLabels(labels);

        if (existing == null) {
            final Lease toCreate = new LeaseBuilder()
                    .withNewMetadata()
                    .withName(name)
                    .withNamespace(ns)
                    .withLabels(labels)
                    .withOwnerReferences(poolTopologyResolver.leaseOwnerReferences())
                    .endMetadata()
                    .withNewSpec()
                    .withLeaseDurationSeconds(leaseDuration)
                    .endSpec()
                    .build();

            try {
                LOG.debug("Creating lease {} in namespace {}", toCreate.getMetadata().getName(), ns);
                return Optional.of(client.leases().inNamespace(ns).resource(toCreate).create());
            } catch (KubernetesClientException e) {
                LOG.debug("Failed to create lease {} in namespace {}. Got: {}", toCreate.getMetadata().getName(), ns,
                        e.getMessage());
                // conflict - race condition while trying to update the same resource
                // we get the latest version and return
                if (e.getStatus().getCode() == 409) {
                    return Optional.ofNullable(client.leases().inNamespace(ns).withName(name).get());
                }
                throw e;
            }
        }
        LOG.debug("Lease {} in namespace {} already exists, attempt to reconcile managed fields.",
                existing.getMetadata().getName(), ns);
        return Optional.of(updateManagedFields(existing, labels, leaseDuration));
    }

    /**
     * Renews the given lease.
     *
     * @param leaseName the Lease name
     * @param holderIdentity usually the podId {@link KubeInfoStrategy#podName()}
     * @return an optional including the lease
     */
    public Optional<Lease> renewLease(String leaseName, String holderIdentity) {
        final String ns = kubeInfo.namespace();
        final Lease lease = client.leases().inNamespace(ns).withName(leaseName).get();
        if (lease == null) {
            return Optional.empty();
        }
        return renewLease(lease, holderIdentity);
    }

    private Optional<Lease> renewLease(Lease lease, String holderIdentity) {
        LOG.debug("Attempting to renew lease: {} for {}", lease.getMetadata().getName(), holderIdentity);
        final String ns = kubeInfo.namespace();

        if (lease.getSpec() == null)
            lease = new LeaseBuilder(lease).withNewSpec().endSpec().build();

        final String previousHolder = lease.getSpec().getHolderIdentity();
        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        lease.getSpec().setRenewTime(now);
        lease.getSpec().setHolderIdentity(holderIdentity);
        if (lease.getSpec().getAcquireTime() == null) {
            lease.getSpec().setAcquireTime(now);
        }

        if (previousHolder != null && !previousHolder.isBlank() && !holderIdentity.equals(previousHolder)) {
            Integer transitions = lease.getSpec().getLeaseTransitions();
            lease.getSpec().setLeaseTransitions(transitions == null ? 1 : transitions + 1);
            lease.getSpec().setAcquireTime(now);
        }

        try {
            final Lease renewedLease = client.leases().inNamespace(ns).resource(lease).update();
            LOG.debug("Renewed lease {} on namespace {} at {} for pod {}", renewedLease.getMetadata().getName(), ns,
                    renewedLease.getSpec().getRenewTime(), renewedLease.getSpec().getHolderIdentity());
            return Optional.of(renewedLease);
        } catch (KubernetesClientException ex) {
            if (ex.getStatus().getCode() == 409) {
                LOG.debug("Pod {} Failed to renew lease {} on namespace {}", kubeInfo.podName(), lease.getMetadata().getName(),
                        ns, ex);
                return Optional.empty();
            }
            throw ex;
        }
    }

    /**
     * Try to acquire the leader lease for the given wanna-be leader.
     *
     * @param holderIdentity usually the podId {@link KubeInfoStrategy#podName()}
     * @param poolLeaderLeaseName the Kubernetes leader lease name
     * @return true if acquired the lease
     */
    public boolean tryAcquireLeaderLease(String holderIdentity, String poolLeaderLeaseName) {
        LOG.debug("Attempt to acquire a leader lease {} for {}", poolLeaderLeaseName, holderIdentity);
        Lease lease = client.leases().inNamespace(kubeInfo.namespace()).withName(poolLeaderLeaseName).get();
        if (lease == null)
            lease = createOrUpdateLeaderLease(poolLeaderLeaseName).orElse(null);

        if (lease == null)
            return false;

        return isAvailableLease(lease, holderIdentity) && renewLease(lease, holderIdentity).isPresent();
    }

    /**
     * Try to acquire a lease for the wanna-be worker in the cluster
     *
     * @param holderIdentity usually the podId {@link KubeInfoStrategy#podName()}
     * @param poolName the name of this pool
     * @return an {@link Optional} of the acquired {@link Lease}
     */
    public Optional<Lease> tryAcquireMemberLease(String holderIdentity, String poolName) {
        LOG.debug("Attempt to acquire a member lease on pool {} for {}", poolName, holderIdentity);
        List<Lease> leases = client.leases().inNamespace(kubeInfo.namespace())
                .withLabelSelector(new LabelSelectorBuilder()
                        .withMatchLabels(Map.of(POOL_NAME_LABEL_KEY, poolName, POOL_IS_LEADER_KEY, "false"))
                        .build())
                .list()
                .getItems();
        if (leases == null || leases.isEmpty())
            return Optional.empty();

        // mine first
        for (Lease l : leases) {
            if (l.getSpec() != null && holderIdentity.equals(l.getSpec().getHolderIdentity())) {
                Optional<Lease> renewed = renewLease(l, holderIdentity);
                if (renewed.isPresent())
                    return renewed;
            }
        }

        for (Lease l : leases) {
            if (isAvailableLease(l, holderIdentity)) {
                Optional<Lease> renewed = renewLease(l, holderIdentity);
                if (renewed.isPresent()) {
                    return renewed;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Attempt to release the Lease.
     *
     * @param holderIdentity usually the podId {@link KubeInfoStrategy#podName()}
     * @param leaseName the known lease name
     * @return true if release has been done correctly.
     */
    public boolean releaseLease(String holderIdentity, String leaseName) {
        LOG.debug("Attempt to release the lease {} for {}", leaseName, holderIdentity);
        Lease lease = client.leases().inNamespace(kubeInfo.namespace()).withName(leaseName).get();
        if (lease == null || lease.getSpec() == null)
            return true;

        if (lease.getSpec().getHolderIdentity().equals(holderIdentity)) {
            lease.getSpec().setHolderIdentity("");
            lease.getSpec().setRenewTime(null);
        } else
            return true;

        return client.leases().inNamespace(kubeInfo.namespace()).resource(lease).update() != null;
    }

    private boolean isAvailableLease(Lease lease, String holderIdentity) {
        if (lease.getSpec() == null)
            return true;

        String currHolder = lease.getSpec().getHolderIdentity();
        ZonedDateTime renew = lease.getSpec().getRenewTime();
        Integer dur = lease.getSpec().getLeaseDurationSeconds();

        if (renew == null || currHolder == null || currHolder.isBlank()) {
            return true;
        }
        if (dur == null)
            return true;

        boolean isExpired = renew.plus(Duration.ofSeconds(dur)).isBefore(ZonedDateTime.now(ZoneOffset.UTC));

        return isExpired || holderIdentity.equals(currHolder);
    }

    private Map<String, String> addDefaultLabels(Map<String, String> labels) {
        Map<String, String> out = new HashMap<>(BASE_LABELS);
        if (labels != null) {
            out.putAll(labels);
        }
        out.put(POOL_NAME_LABEL_KEY, poolConfig.name());
        return out;
    }

    private Lease updateManagedFields(Lease existing,
            Map<String, String> labels, Integer leaseDuration) {
        Map<String, String> mergedLabels = mergeMaps(existing.getMetadata() != null ? existing.getMetadata().getLabels() : null,
                labels);

        LOG.debug("Attempt to update managed fields for lease {}", existing.getMetadata().getName());

        Integer currentTtl = existing.getSpec() != null ? existing.getSpec().getLeaseDurationSeconds() : null;
        boolean ttlDiffers = currentTtl == null || currentTtl.intValue() != leaseDuration;

        boolean labelsDiffers = !mapsEqual(existing.getMetadata() != null ? existing.getMetadata().getLabels() : null,
                mergedLabels);

        if (!ttlDiffers && !labelsDiffers) {
            return existing;
        }

        LeaseBuilder b = new LeaseBuilder(existing)
                .editOrNewMetadata()
                .withLabels(mergedLabels)
                .endMetadata()
                .editOrNewSpec()
                .withLeaseDurationSeconds(leaseDuration)
                .endSpec();

        Lease updated = b.build();
        String ns = kubeInfo.namespace();
        try {
            return client.leases().inNamespace(ns).resource(updated).update();
        } catch (KubernetesClientException e) {
            if (e.getCode() == 409) {
                Lease latest = client.leases().inNamespace(ns).withName(existing.getMetadata().getName()).get();
                return latest != null ? latest : existing;
            }
            throw e;
        }
    }

    public Optional<Integer> desiredReplicas() {
        return poolTopologyResolver.desiredReplicas();
    }

}
