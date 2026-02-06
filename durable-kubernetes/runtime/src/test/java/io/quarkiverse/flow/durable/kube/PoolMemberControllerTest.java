package io.quarkiverse.flow.durable.kube;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Inject
    PoolMemberController controller;

    @InjectMock
    LeaseService leaseService;
    @InjectMock
    KubeInfoStrategy kubeInfo;

    @BeforeEach
    void resetControllerState() throws Exception {
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
    void firstAcquire_success_setsLeaseName() {
        when(kubeInfo.podName()).thenReturn("pod-1");

        when(leaseService.tryAcquireMemberLease("pod-1", "mypool"))
                .thenReturn(Optional.of(new LeaseBuilder()
                        .withNewMetadata().withName("flow-pool-member-mypool-00").endMetadata()
                        .withNewSpec().withHolderIdentity("pod-1").endSpec()
                        .build()));

        boolean ok = controller.acquireLease();

        assertTrue(ok);
        assertTrue(controller.hasLease());
        verify(leaseService).tryAcquireMemberLease("pod-1", "mypool");
    }

    @Test
    void renewFails_resetsAndNextAcquireCallsTryAcquireAgain() throws Exception {
        when(kubeInfo.podName()).thenReturn("pod-1");

        // seed internal state
        var f = PoolMemberController.class.getDeclaredField("leaseName");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<String> ref = (AtomicReference<String>) f.get(controller);
        ref.set("flow-pool-member-mypool-00");

        when(leaseService.renewLease("flow-pool-member-mypool-00", "mypool"))
                .thenReturn(Optional.empty());

        boolean ok = controller.acquireLease();
        assertFalse(ok);
        assertFalse(controller.hasLease());

        // next cycle: attempt new acquire
        when(leaseService.tryAcquireMemberLease("pod-1", "mypool"))
                .thenReturn(Optional.empty());

        controller.acquireLease();
        verify(leaseService).tryAcquireMemberLease("pod-1", "mypool");
    }
}
