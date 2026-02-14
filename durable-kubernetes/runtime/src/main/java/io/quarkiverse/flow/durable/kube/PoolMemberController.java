package io.quarkiverse.flow.durable.kube;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.quarkus.runtime.Startup;

@Singleton
@Startup
public class PoolMemberController extends PoolController {

    private static final String POOL_MEMBER_SCHEDULER_FMT = "flow-pool-member-scheduler-%s-%s";

    private static final Logger LOG = LoggerFactory.getLogger(PoolMemberController.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> leaseName = new AtomicReference<>();

    @Inject
    PoolConfig poolConfig;

    @Inject
    LeaseGroupConfig leaseConfig;

    @Inject
    SchedulerGroupConfig schedulerConfig;

    @Inject
    Event<MemberLeaseEvent> leaseEvents;

    @Override
    public void run() {
        LOG.debug("Attempt to run pool member controller scheduler");
        if (!leaseConfig.member().enabled())
            return;

        if (!running.compareAndSet(false, true))
            return;

        try {
            if (!acquireLease())
                LOG.warn(
                        "Flow: Failed to acquire lease on {}, waiting for next scheduled cycle to try again. Won't process any new workflows until there",
                        kubeInfo.podName());
        } catch (Exception e) {
            LOG.warn("Lease acquisition failed on pod {}", kubeInfo.podName(), e);
        } finally {
            running.set(false);
        }
    }

    boolean acquireLease() {
        String current = leaseName.get();
        if (current == null) {
            Optional<Lease> lease = leaseService.tryAcquireMemberLease(kubeInfo.podName(), poolConfig.name());
            if (lease.isPresent()) {
                leaseName.set(lease.get().getMetadata().getName());
                leaseEvents.fire(new MemberLeaseEvent(
                        MemberLeaseEvent.Type.ACQUIRED,
                        poolConfig.name(),
                        kubeInfo.podName(),
                        lease.get().getMetadata().getName()));
                return true;
            }
            return false;
        }

        Optional<Lease> lease = leaseService.renewLease(current, kubeInfo.podName());
        // if we return false, on next scheduler run it will try getting a new lease
        if (lease.isEmpty()) {
            leaseName.set(null);
            leaseEvents.fire(new MemberLeaseEvent(
                    MemberLeaseEvent.Type.LOST,
                    poolConfig.name(),
                    kubeInfo.podName(),
                    current));
            return false;
        }
        return true;
    }

    @Override
    protected void afterRelease(boolean released) {
        if (released) {
            leaseEvents.fire(new MemberLeaseEvent(
                    MemberLeaseEvent.Type.RELEASED,
                    poolConfig.name(),
                    kubeInfo.podName(),
                    leaseName.get()));
        }
    }

    public boolean hasLease() {
        return leaseName.get() != null;
    }

    @Override
    protected String leaseName() {
        return leaseName.get();
    }

    @Override
    protected String scheduledExecutorName() {
        return String.format(POOL_MEMBER_SCHEDULER_FMT, poolConfig.name(), kubeInfo.podName());
    }

    @Override
    protected SchedulerGroupConfig.SchedulerConfig schedulerConfig() {
        return schedulerConfig.member();
    }

    @Override
    protected LeaseGroupConfig.LeaseConfig leaseConfig() {
        return leaseConfig.member();
    }
}
