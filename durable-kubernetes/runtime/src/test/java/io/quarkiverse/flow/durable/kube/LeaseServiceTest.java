package io.quarkiverse.flow.durable.kube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.flow.durable.kube.config.FlowDurableKubeConfig;
import io.quarkiverse.flow.durable.kube.config.PoolConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@QuarkusTest
@WithKubernetesTestServer
public class LeaseServiceTest {

    @Inject
    KubernetesClient client;

    @Inject
    LeaseService leaseService;

    @InjectMock
    KubeInfoStrategy kubeInfo;

    @Inject
    FlowDurableKubeConfig config;

    @Inject
    PoolConfig poolConfig;

    @BeforeEach
    void setup() {
        when(kubeInfo.namespace()).thenReturn("default");
        when(kubeInfo.podName()).thenReturn("pod-1");

        // Clean for test isolation
        client.leases().inNamespace("default").delete();
        client.pods().inNamespace("default").delete();
        client.apps().replicaSets().inNamespace("default").delete();
        client.apps().deployments().inNamespace("default").delete();

        // Deployment
        client.apps().deployments().inNamespace("default").resource(new DeploymentBuilder()
                .withNewMetadata()
                .withName("dep-1")
                .withNamespace("default")
                .withUid("dep-uid-1")
                .endMetadata()
                .withNewSpec()
                .withReplicas(3)
                .withNewSelector().addToMatchLabels("app", "x").endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels("app", "x").endMetadata()
                .withNewSpec().addNewContainer().withName("c").withImage("i").endContainer().endSpec()
                .endTemplate()
                .endSpec()
                .build()).create();

        // ReplicaSet owned by Deployment
        client.apps().replicaSets().inNamespace("default").resource(new ReplicaSetBuilder()
                .withNewMetadata()
                .withName("rs-1")
                .withNamespace("default")
                .withOwnerReferences(new OwnerReferenceBuilder()
                        .withApiVersion("apps/v1")
                        .withKind("Deployment")
                        .withName("dep-1")
                        .withUid("dep-uid-1")
                        .withController(true)
                        .build())
                .endMetadata()
                .withNewSpec()
                .withNewSelector().addToMatchLabels("app", "x").endSelector()
                .withNewTemplate()
                .withNewMetadata().addToLabels("app", "x").endMetadata()
                .withNewSpec().addNewContainer().withName("c").withImage("i").endContainer().endSpec()
                .endTemplate()
                .endSpec()
                .build()).create();

        // Pod owned by ReplicaSet (this is what resolveCurrentDeployment() walks)
        client.pods().inNamespace("default").resource(new PodBuilder()
                .withNewMetadata()
                .withName("pod-1")
                .withNamespace("default")
                .withOwnerReferences(new OwnerReferenceBuilder()
                        .withApiVersion("apps/v1")
                        .withKind("ReplicaSet")
                        .withName("rs-1")
                        .withUid("rs-uid-1")
                        .withController(true)
                        .build())
                .endMetadata()
                .withNewSpec()
                .addNewContainer().withName("c").withImage("i").endContainer()
                .endSpec()
                .build()).create();
    }

    @Test
    void createOrUpdateMemberLease_createsLeaseWithOwnerRefAndLabels() {
        Optional<Lease> created = leaseService.createOrUpdateMemberLease("member-1");
        assertTrue(created.isPresent());

        Lease lease = client.leases().inNamespace("default").withName("member-1").get();
        assertNotNull(lease);

        // ownerReference -> Deployment
        assertFalse(lease.getMetadata().getOwnerReferences().isEmpty());
        var owner = lease.getMetadata().getOwnerReferences().get(0);
        assertEquals("Deployment", owner.getKind());
        assertEquals("apps/v1", owner.getApiVersion());
        assertEquals("dep-1", owner.getName());

        // labels
        var labels = lease.getMetadata().getLabels();
        assertEquals("quarkus-flow", labels.get("app.kubernetes.io/managed-by"));
        assertEquals("durable", labels.get("app.kubernetes.io/component"));
        assertEquals(poolConfig.name(), labels.get("io.quarkiverse.flow.durable.k8s/pool"));

        // spec TTL set
        assertNotNull(lease.getSpec());
        assertNotNull(lease.getSpec().getLeaseDurationSeconds());
    }

    @Test
    void tryAcquireLeaderLease_createsAndRenews() {
        boolean ok = leaseService.tryAcquireLeaderLease("pod-1", "leader-1");
        assertTrue(ok);

        Lease lease = client.leases().inNamespace("default").withName("leader-1").get();
        assertNotNull(lease.getSpec());
        assertEquals("pod-1", lease.getSpec().getHolderIdentity());
        assertNotNull(lease.getSpec().getRenewTime());
    }

    @Test
    void tryAcquireMemberLease_stealsExpiredLease() {
        // Create an expired lease with correct labels
        Lease expired = new LeaseBuilder()
                .withNewMetadata()
                .withName("m-expired")
                .withNamespace("default")
                .withLabels(Map.of(
                        "app.kubernetes.io/managed-by", "quarkus-flow",
                        "app.kubernetes.io/component", "durable",
                        "io.quarkiverse.flow.durable.k8s/pool", poolConfig.name(),
                        "io.quarkiverse.flow.durable.k8s/is-leader", "false"))
                .endMetadata()
                .withNewSpec()
                .withHolderIdentity("pod-2")
                .withLeaseDurationSeconds(5)
                .withRenewTime(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(60))
                .endSpec()
                .build();

        client.leases().inNamespace("default").resource(expired).create();

        Lease acquired = leaseService.tryAcquireMemberLease("pod-1", poolConfig.name())
                .orElseThrow();

        assertEquals("pod-1", acquired.getSpec().getHolderIdentity());
        assertNotNull(acquired.getSpec().getRenewTime());
    }

    @Test
    void tryReacquireSpecificLease_succeeds_whenAvailable() {
        Lease available = new LeaseBuilder()
                .withNewMetadata()
                .withName("m-reacquire")
                .withNamespace("default")
                .withLabels(Map.of(
                        "app.kubernetes.io/managed-by", "quarkus-flow",
                        "app.kubernetes.io/component", "durable",
                        "io.quarkiverse.flow.durable.k8s/pool", poolConfig.name(),
                        "io.quarkiverse.flow.durable.k8s/is-leader", "false"))
                .endMetadata()
                .withNewSpec()
                .withHolderIdentity("")
                .withLeaseDurationSeconds(30)
                .endSpec()
                .build();
        client.leases().inNamespace("default").resource(available).create();

        Optional<Lease> result = leaseService.tryReacquireSpecificLease("pod-1", "m-reacquire");

        assertTrue(result.isPresent());
        assertEquals("pod-1", result.get().getSpec().getHolderIdentity());
    }

    @Test
    void tryReacquireSpecificLease_fails_whenDeleted() {
        Optional<Lease> result = leaseService.tryReacquireSpecificLease("pod-1", "nonexistent-lease");

        assertFalse(result.isPresent());
    }

    @Test
    void tryReacquireSpecificLease_fails_whenHeldByOtherAndNotExpired() {
        Lease held = new LeaseBuilder()
                .withNewMetadata()
                .withName("m-held")
                .withNamespace("default")
                .withLabels(Map.of(
                        "app.kubernetes.io/managed-by", "quarkus-flow",
                        "app.kubernetes.io/component", "durable",
                        "io.quarkiverse.flow.durable.k8s/pool", poolConfig.name(),
                        "io.quarkiverse.flow.durable.k8s/is-leader", "false"))
                .endMetadata()
                .withNewSpec()
                .withHolderIdentity("pod-2")
                .withLeaseDurationSeconds(300)
                .withRenewTime(ZonedDateTime.now(ZoneOffset.UTC))
                .endSpec()
                .build();
        client.leases().inNamespace("default").resource(held).create();

        Optional<Lease> result = leaseService.tryReacquireSpecificLease("pod-1", "m-held");

        assertFalse(result.isPresent());
    }

    @Test
    void existingLease_isUpdated_whenManagedFieldsDiffer() {
        String pool = poolConfig.name();
        String leaseName = "flow-pool-member-" + pool + "-00";

        client.leases().inNamespace("default").resource(new LeaseBuilder()
                .withNewMetadata()
                .withName(leaseName)
                .withNamespace("default")
                .withLabels(Map.of("foo", "bar")) // missing base labels + pool labels
                .endMetadata()
                .withNewSpec()
                .withLeaseDurationSeconds(1) // wrong ttl
                .endSpec()
                .build()).create();

        // Act
        Optional<Lease> updatedOpt = leaseService.createOrUpdateMemberLease(leaseName);

        // Assert
        assertTrue(updatedOpt.isPresent());

        var updated = client.leases().inNamespace("default").withName(leaseName).get();
        assertEquals(config.lease().member().duration(), updated.getSpec().getLeaseDurationSeconds());

        // base labels enforced + pool/is-leader labels enforced + existing labels preserved
        Map<String, String> labels = updated.getMetadata().getLabels();
        assertEquals("quarkus-flow", labels.get("app.kubernetes.io/managed-by"));
        assertEquals("durable", labels.get("app.kubernetes.io/component"));
        assertEquals(pool, labels.get("io.quarkiverse.flow.durable.k8s/pool"));
        assertEquals("false", labels.get("io.quarkiverse.flow.durable.k8s/is-leader"));
        assertEquals("bar", labels.get("foo"));
    }

    @Test
    void renewLease_refusesToSteal_whenLeaseHeldByOtherPod() {
        // Create a lease held by pod-2
        Lease heldByPodTwo = new LeaseBuilder()
                .withNewMetadata()
                .withName("m-held-by-other")
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withHolderIdentity("pod-2")
                .withLeaseDurationSeconds(300)
                .withRenewTime(ZonedDateTime.now(ZoneOffset.UTC))
                .endSpec()
                .build();

        client.leases().inNamespace("default").resource(heldByPodTwo).create();

        // Try to renew as pod-1 (different holder)
        Optional<Lease> result = leaseService.renewLease("m-held-by-other", "pod-1");

        // Should refuse the steal
        assertFalse(result.isPresent());

        // Verify the holder was not changed
        Lease lease = client.leases().inNamespace("default").withName("m-held-by-other").get();
        assertEquals("pod-2", lease.getSpec().getHolderIdentity());
    }

    @Test
    void renewLease_succeeds_whenLeaseHeldBySamePod() {
        // Create a lease held by pod-1
        Lease heldByPodOne = new LeaseBuilder()
                .withNewMetadata()
                .withName("m-held-by-self")
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withHolderIdentity("pod-1")
                .withLeaseDurationSeconds(300)
                .withRenewTime(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(30))
                .endSpec()
                .build();

        client.leases().inNamespace("default").resource(heldByPodOne).create();

        // Renew as pod-1 (same holder)
        Optional<Lease> result = leaseService.renewLease("m-held-by-self", "pod-1");

        // Should succeed
        assertTrue(result.isPresent());
        assertEquals("pod-1", result.get().getSpec().getHolderIdentity());

        // Verify renewTime was updated
        Lease lease = client.leases().inNamespace("default").withName("m-held-by-self").get();
        assertNotNull(lease.getSpec().getRenewTime());
        assertTrue(lease.getSpec().getRenewTime().isAfter(heldByPodOne.getSpec().getRenewTime()));
    }

    @Test
    void tryAcquireMemberLease_canAcquireExpiredLease() {
        // Create an expired lease held by pod-2
        Lease expired = new LeaseBuilder()
                .withNewMetadata()
                .withName("m-expired-other")
                .withNamespace("default")
                .withLabels(Map.of(
                        "app.kubernetes.io/managed-by", "quarkus-flow",
                        "app.kubernetes.io/component", "durable",
                        "io.quarkiverse.flow.durable.k8s/pool", poolConfig.name(),
                        "io.quarkiverse.flow.durable.k8s/is-leader", "false"))
                .endMetadata()
                .withNewSpec()
                .withHolderIdentity("pod-2")
                .withLeaseDurationSeconds(5)
                .withRenewTime(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(60))
                .endSpec()
                .build();

        client.leases().inNamespace("default").resource(expired).create();

        // pod-1 should be able to acquire the expired lease
        Optional<Lease> result = leaseService.tryAcquireMemberLease("pod-1", poolConfig.name());

        assertTrue(result.isPresent());
        assertEquals("pod-1", result.get().getSpec().getHolderIdentity());
    }
}
