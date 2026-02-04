package io.quarkiverse.flow.durable.kube;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PoolLeaderControllerTest {

    @Inject
    PoolLeaderController controller;

    @InjectMock
    LeaseService leaseService;

    @InjectMock
    KubeInfoStrategy kubeInfo;

    @Test
    void notLeader_skipsReconcile() {
        when(kubeInfo.podName()).thenReturn("pod-1");
        when(leaseService.tryAcquireLeaderLease(eq("pod-1"), anyString())).thenReturn(false);

        boolean ok = controller.reconcile();

        assertFalse(ok);
        verify(leaseService, never()).createOrUpdateMemberLease(anyString());
    }

    @Test
    void leader_reconcilesAllMembers() {
        when(kubeInfo.podName()).thenReturn("pod-1");
        when(leaseService.tryAcquireLeaderLease(eq("pod-1"), contains("flow-pool-leader-"))).thenReturn(true);
        when(leaseService.desiredReplicas()).thenReturn(Optional.of(3));

        when(leaseService.createOrUpdateMemberLease(anyString()))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewSpec().and()
                        .withNewMetadata().endMetadata()
                        .build()));

        boolean ok = controller.reconcile();

        assertTrue(ok);

        verify(leaseService).createOrUpdateMemberLease("flow-pool-member-mypool-00");
        verify(leaseService).createOrUpdateMemberLease("flow-pool-member-mypool-01");
        verify(leaseService).createOrUpdateMemberLease("flow-pool-member-mypool-02");
    }
}
