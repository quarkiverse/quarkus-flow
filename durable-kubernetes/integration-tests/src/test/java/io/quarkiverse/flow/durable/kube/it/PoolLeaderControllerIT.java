package io.quarkiverse.flow.durable.kube.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.flow.durable.kube.KubeInfoStrategy;
import io.quarkiverse.flow.durable.kube.LeaseService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PoolLeaderControllerIT {

    @Inject
    KubernetesClient client;
    @Inject
    KubeInfoStrategy kubeInfo;

    @Test
    void leaderCreatesLeaderAndMemberLeases() {
        String ns = kubeInfo.namespace();

        // wait (controller uses delayed=5..11s + interval; give it breathing room)
        Lease leader = awaitLeaderLeaseHeldBy(ns, "flow-pool-leader-mypool", kubeInfo.podName(), Duration.ofSeconds(30));
        assertNotNull(leader, "leader should not be null");
        assertNotNull(leader.getSpec(), "leader spec must exist");
        assertEquals(kubeInfo.podName(), leader.getSpec().getHolderIdentity());

        // member leases should exist (replicas=3 in fixtures)
        List<Lease> members = client.leases().inNamespace(ns)
                .withLabel(LeaseService.POOL_NAME_LABEL_KEY, "mypool")
                .withLabel(LeaseService.POOL_IS_LEADER_KEY, "false")
                .list()
                .getItems();

        // leader reconciles exactly "replicas" member leases
        assertTrue(members.size() >= 3, "expected at least 3 member leases, got " + members.size());

        assertNotNull(client.leases().inNamespace(ns).withName("flow-pool-member-mypool-00").get());
        assertNotNull(client.leases().inNamespace(ns).withName("flow-pool-member-mypool-01").get());
        assertNotNull(client.leases().inNamespace(ns).withName("flow-pool-member-mypool-02").get());
    }

    private Lease awaitLeaderLeaseHeldBy(String ns, String name, String podName, Duration timeout) {
        return await()
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(250))
                .ignoreExceptions()
                .until(
                        () -> client.leases().inNamespace(ns).withName(name).get(),
                        lease -> lease != null
                                && lease.getSpec() != null
                                && podName.equals(lease.getSpec().getHolderIdentity()));
    }
}
