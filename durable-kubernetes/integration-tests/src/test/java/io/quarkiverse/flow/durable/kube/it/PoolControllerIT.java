package io.quarkiverse.flow.durable.kube.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.flow.durable.kube.KubeInfoStrategy;
import io.quarkiverse.flow.durable.kube.LeaseService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PoolControllerIT {

    @Inject
    KubernetesClient client;
    @Inject
    KubeInfoStrategy kubeInfo;

    @Test
    void checkLeasesAndPoolState() {
        String ns = kubeInfo.namespace();
        String pod = kubeInfo.podName();

        // Leader lease should exist and be held by this pod
        Lease leader = awaitLeaseHeldBy(ns, pod, Duration.ofSeconds(30));
        assertNotNull(leader.getSpec());
        assertEquals(pod, leader.getSpec().getHolderIdentity());

        // Member leases should exist (fixtures replicas=3)
        List<Lease> members = awaitMemberLeases(ns, Duration.ofSeconds(30));

        // Verify expected member names exist (exact set)
        Set<String> names = members.stream()
                .map(l -> l.getMetadata().getName())
                .collect(Collectors.toSet());

        assertTrue(names.contains("flow-pool-member-mypool-00"));
        assertTrue(names.contains("flow-pool-member-mypool-01"));
        assertTrue(names.contains("flow-pool-member-mypool-02"));

        // Verify labels are correct
        for (Lease l : members) {
            assertNotNull(l.getMetadata());
            assertNotNull(l.getMetadata().getLabels());
            assertEquals("mypool", l.getMetadata().getLabels().get(LeaseService.POOL_NAME_LABEL_KEY));
            assertEquals("false", l.getMetadata().getLabels().get(LeaseService.POOL_IS_LEADER_KEY));
        }
        // Verify ownerReference points to the Deployment (garbage collection)
        for (Lease l : members) {
            List<OwnerReference> owners = l.getMetadata().getOwnerReferences();
            assertNotNull(owners, "ownerReferences should exist for " + l.getMetadata().getName());

            boolean hasDeploymentOwner = owners.stream()
                    .anyMatch(o -> "Deployment".equals(o.getKind()) && o.getName() != null && !o.getName().isBlank());

            assertTrue(hasDeploymentOwner, "expected Deployment ownerReference for " + l.getMetadata().getName());
        }
    }

    @Test
    void leaderCreatesLeaderAndMemberLeases_andAlsoAcquiresAMemberLease() {
        String ns = kubeInfo.namespace();
        String pod = kubeInfo.podName();

        // leader held
        Lease leader = awaitLeaseHeldBy(ns, pod, Duration.ofSeconds(30));
        assertNotNull(leader.getSpec());
        assertEquals(pod, leader.getSpec().getHolderIdentity());

        // member leases exist
        List<Lease> members = awaitMemberLeases(ns, Duration.ofSeconds(30));
        assertTrue(members.size() >= 3);

        // leader is also a member: at least one member lease must be held by the same pod
        Lease myMemberLease = awaitAnyMemberLeaseHeldBy(ns, pod, Duration.ofSeconds(30));
        assertNotNull(myMemberLease, "expected at least one member lease held by leader pod");
        assertEquals(pod, myMemberLease.getSpec().getHolderIdentity());

        // sanity: ensure it's not marked as leader
        assertEquals("false", myMemberLease.getMetadata().getLabels().get(LeaseService.POOL_IS_LEADER_KEY));
    }

    private Lease awaitAnyMemberLeaseHeldBy(String ns, String podName, Duration timeout) {
        return await()
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(250))
                .ignoreExceptions()
                .until(
                        () -> {
                            List<Lease> leases = client.leases().inNamespace(ns)
                                    .withLabel(LeaseService.POOL_NAME_LABEL_KEY, "mypool")
                                    .withLabel(LeaseService.POOL_IS_LEADER_KEY, "false")
                                    .list()
                                    .getItems();

                            if (leases == null)
                                return null;

                            return leases.stream()
                                    .filter(l -> l.getSpec() != null)
                                    .filter(l -> podName.equals(l.getSpec().getHolderIdentity()))
                                    .findFirst()
                                    .orElse(null);
                        },
                        Objects::nonNull);
    }

    private Lease awaitLeaseHeldBy(String ns, String holderIdentity, Duration timeout) {
        return await()
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(250))
                .ignoreExceptions()
                .until(
                        () -> client.leases().inNamespace(ns).withName("flow-pool-leader-mypool").get(),
                        lease -> lease != null
                                && lease.getSpec() != null
                                && holderIdentity.equals(lease.getSpec().getHolderIdentity()));
    }

    private List<Lease> awaitMemberLeases(String ns, Duration timeout) {
        return await()
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(250))
                .ignoreExceptions()
                .until(
                        () -> client.leases().inNamespace(ns)
                                .withLabel(LeaseService.POOL_NAME_LABEL_KEY, "mypool")
                                .withLabel(LeaseService.POOL_IS_LEADER_KEY, "false")
                                .list()
                                .getItems(),
                        leases -> leases != null && leases.size() >= 3);
    }
}
