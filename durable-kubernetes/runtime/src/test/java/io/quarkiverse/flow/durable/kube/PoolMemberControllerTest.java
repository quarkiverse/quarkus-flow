package io.quarkiverse.flow.durable.kube;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PoolMemberControllerTest {

    private static final String POD = "pod-1";
    private static final String POOL = "mypool";
    private static final String LEASE = "flow-pool-member-mypool-00";

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

        var f = PoolMemberController.class.getDeclaredField("leaseName");
        f.setAccessible(true);

        @SuppressWarnings("unchecked")
        AtomicReference<String> ref = (AtomicReference<String>) f.get(controller);
        ref.set(null);
    }

    @Test
    void disabled_doesNothing() {
        controller.run();
        verifyNoInteractions(leaseService);
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

    private void seedLeaseName(String leaseName) throws Exception {
        var f = PoolMemberController.class.getDeclaredField("leaseName");
        f.setAccessible(true);

        @SuppressWarnings("unchecked")
        AtomicReference<String> ref = (AtomicReference<String>) f.get(controller);
        ref.set(leaseName);
    }
}
