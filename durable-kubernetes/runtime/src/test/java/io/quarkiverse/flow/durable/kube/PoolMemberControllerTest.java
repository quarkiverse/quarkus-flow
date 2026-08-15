package io.quarkiverse.flow.durable.kube;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;

@QuarkusComponentTest
class PoolMemberControllerTest {

    private static final String POD = "pod-1";
    private static final String POOL = "mypool";
    private static final String LEASE = "flow-pool-member-mypool-00";
    private static final String LEASE_OTHER = "flow-pool-member-mypool-01";

    @Inject
    PoolMemberController controller;

    @InjectMock
    LeaseService leaseService;

    @InjectMock
    KubeInfoStrategy kubeInfo;

    @BeforeEach
    void resetControllerState() throws Exception {
        // make sure podName is always stable in these tests
        when(kubeInfo.podName()).thenReturn(POD);

        Object target = ClientProxy.unwrap(controller);

        var f = PoolMemberController.class.getDeclaredField("leaseName");
        f.setAccessible(true);

        @SuppressWarnings("unchecked")
        AtomicReference<String> ref = (AtomicReference<String>) f.get(target);
        ref.set(null);

        var bf = PoolMemberController.class.getDeclaredField("boundLeaseName");
        bf.setAccessible(true);
        bf.set(target, null);

        var ra = PoolMemberController.class.getDeclaredField("reacquireAttempts");
        ra.setAccessible(true);
        ((AtomicInteger) ra.get(target)).set(0);
    }

    @Test
    void disabled_doesNothing() {
        controller.run();
        verify(leaseService, never()).tryAcquireMemberLease(anyString(), anyString());
    }

    @Test
    void firstAcquire_success_setsLeaseName_andUsesPodAsHolder() {
        when(leaseService.tryAcquireMemberLease(POD, POOL))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName(LEASE).endMetadata()
                        .withNewSpec().withHolderIdentity(POD).endSpec()
                        .build()));

        boolean ok = controller.acquireLease();

        assertTrue(ok);
        assertTrue(controller.hasLease());

        verify(leaseService).tryAcquireMemberLease(POD, POOL);
        verify(leaseService, never()).renewLease(anyString(), anyString());
    }

    @Test
    void renewSuccess_callsRenewWithPodName_notPoolName_andDoesNotReacquire() throws Exception {
        seedLeaseName(LEASE);

        when(leaseService.renewLease(LEASE, POD))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName(LEASE).endMetadata()
                        .withNewSpec().withHolderIdentity(POD).endSpec()
                        .build()));

        boolean ok = controller.acquireLease();

        assertTrue(ok);
        assertTrue(controller.hasLease());

        verify(leaseService).renewLease(LEASE, POD);
        verify(leaseService, never()).renewLease(anyString(), eq(POOL));
        verify(leaseService, never()).tryAcquireMemberLease(anyString(), anyString());
    }

    @Test
    void renewFails_resets_andNextAcquire_callsTryAcquireAgain_andRenewUsesPod() throws Exception {
        seedLeaseName(LEASE);

        when(leaseService.renewLease(LEASE, POD))
                .thenReturn(Optional.empty());

        boolean ok = controller.acquireLease();

        assertFalse(ok);
        assertFalse(controller.hasLease());

        verify(leaseService).renewLease(LEASE, POD);
        verify(leaseService, never()).renewLease(anyString(), eq(POOL));

        // next cycle: attempt new acquire
        when(leaseService.tryAcquireMemberLease(POD, POOL))
                .thenReturn(Optional.empty());

        controller.acquireLease();

        verify(leaseService).tryAcquireMemberLease(POD, POOL);
    }

    @Test
    void afterLoss_onlyBoundLeaseIsAttempted_notAnyLease() {
        // First acquire — binds to LEASE
        when(leaseService.tryAcquireMemberLease(POD, POOL))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName(LEASE).endMetadata()
                        .withNewSpec().withHolderIdentity(POD).endSpec()
                        .build()));

        assertTrue(controller.acquireLease());

        // Renewal fails — lease lost
        when(leaseService.renewLease(LEASE, POD)).thenReturn(Optional.empty());
        assertFalse(controller.acquireLease());
        assertFalse(controller.hasLease());

        // Re-acquire: should only try the bound lease, NOT tryAcquireMemberLease
        when(leaseService.tryReacquireSpecificLease(POD, LEASE))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName(LEASE).endMetadata()
                        .withNewSpec().withHolderIdentity(POD).endSpec()
                        .build()));

        assertTrue(controller.acquireLease());
        assertTrue(controller.hasLease());

        // tryAcquireMemberLease called only once (initial), never again after loss
        verify(leaseService, times(1)).tryAcquireMemberLease(anyString(), anyString());
        verify(leaseService, times(1)).tryReacquireSpecificLease(POD, LEASE);
    }

    @Test
    void afterLoss_reacquireFails_doesNotAcquireDifferentLease() {
        // First acquire — binds to LEASE
        when(leaseService.tryAcquireMemberLease(POD, POOL))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName(LEASE).endMetadata()
                        .withNewSpec().withHolderIdentity(POD).endSpec()
                        .build()));
        assertTrue(controller.acquireLease());

        // Renewal fails — lease lost
        when(leaseService.renewLease(LEASE, POD)).thenReturn(Optional.empty());
        assertFalse(controller.acquireLease());

        // Re-acquire fails — bound lease is gone
        when(leaseService.tryReacquireSpecificLease(POD, LEASE))
                .thenReturn(Optional.empty());

        assertFalse(controller.acquireLease());
        assertFalse(controller.hasLease());

        // Must never fall back to tryAcquireMemberLease (which could grab a different lease)
        verify(leaseService, times(1)).tryAcquireMemberLease(anyString(), anyString());
    }

    @Test
    void afterLoss_reacquireSucceeds_resetsAttemptCounter() throws Exception {
        // First acquire
        when(leaseService.tryAcquireMemberLease(POD, POOL))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName(LEASE).endMetadata()
                        .withNewSpec().withHolderIdentity(POD).endSpec()
                        .build()));
        assertTrue(controller.acquireLease());

        // Lose the lease
        when(leaseService.renewLease(LEASE, POD)).thenReturn(Optional.empty());
        assertFalse(controller.acquireLease());

        // Fail re-acquire twice
        when(leaseService.tryReacquireSpecificLease(POD, LEASE)).thenReturn(Optional.empty());
        assertFalse(controller.acquireLease());
        assertFalse(controller.acquireLease());

        // Succeed on third attempt
        when(leaseService.tryReacquireSpecificLease(POD, LEASE))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName(LEASE).endMetadata()
                        .withNewSpec().withHolderIdentity(POD).endSpec()
                        .build()));
        assertTrue(controller.acquireLease());

        // Lose again
        when(leaseService.renewLease(LEASE, POD)).thenReturn(Optional.empty());
        assertFalse(controller.acquireLease());

        // Counter should have been reset — we can fail again without triggering shutdown
        when(leaseService.tryReacquireSpecificLease(POD, LEASE)).thenReturn(Optional.empty());
        assertFalse(controller.acquireLease());

        Object target = ClientProxy.unwrap(controller);
        var ra = PoolMemberController.class.getDeclaredField("reacquireAttempts");
        ra.setAccessible(true);
        // Should be 1 (one failure since last reset), not 3+ (accumulated)
        assertTrue(((AtomicInteger) ra.get(target)).get() == 1);
    }

    private void seedLeaseName(String leaseName) throws Exception {
        Object target = ClientProxy.unwrap(controller);

        var f = PoolMemberController.class.getDeclaredField("leaseName");
        f.setAccessible(true);

        // 2. Set the value on the real instance
        @SuppressWarnings("unchecked")
        AtomicReference<String> ref = (AtomicReference<String>) f.get(target);
        ref.set(leaseName);
    }
}
