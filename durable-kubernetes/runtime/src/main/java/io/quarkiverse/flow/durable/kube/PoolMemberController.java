package io.quarkiverse.flow.durable.kube;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.quarkiverse.flow.durable.kube.config.LeaseGroupConfig;
import io.quarkiverse.flow.durable.kube.config.PoolConfig;
import io.quarkiverse.flow.durable.kube.config.SchedulerGroupConfig;
import io.quarkus.runtime.Quarkus;

@ApplicationScoped
public class PoolMemberController extends PoolController {

    private static final String POOL_MEMBER_SCHEDULER_FMT = "flow-pool-member-scheduler-%s-%s";

    private static final Logger LOG = LoggerFactory.getLogger(PoolMemberController.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> leaseName = new AtomicReference<>();
    private volatile String boundLeaseName;
    private final AtomicInteger reacquireAttempts = new AtomicInteger(0);

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
            if (!acquireLease()) {
                LOG.warn(
                        "Flow: Failed to acquire lease on {}, waiting for next scheduled cycle to try again. Won't process any new workflows until there",
                        kubeInfo.podName());
                // If the app hasn't successfully bound a lease yet, retry quickly
                // in case the Leader Controller is currently generating the lease.
                if (!hasLease() && executorService != null && !executorService.isShutdown()) {
                    executorService.schedule(this, 2, TimeUnit.SECONDS);
                }
            }
        } catch (Exception e) {
            LOG.warn("Lease acquisition failed on pod {}", kubeInfo.podName(), e);
        } finally {
            running.set(false);
        }
    }

    boolean acquireLease() {
        String current = leaseName.get();
        if (current == null) {
            if (boundLeaseName != null) {
                return reacquireBoundLease();
            }
            Optional<Lease> lease = leaseService.tryAcquireMemberLease(kubeInfo.podName(), poolConfig.name());
            if (lease.isPresent()) {
                String name = lease.get().getMetadata().getName();
                boundLeaseName = name;
                leaseName.set(name);
                leaseEvents.fire(new MemberLeaseEvent(
                        MemberLeaseEvent.Type.ACQUIRED,
                        poolConfig.name(),
                        kubeInfo.podName(),
                        name));
                return true;
            }
            return false;
        }

        Optional<Lease> lease = leaseService.renewLease(current, kubeInfo.podName());
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

    private boolean reacquireBoundLease() {
        Optional<Lease> lease = leaseService.tryReacquireSpecificLease(kubeInfo.podName(), boundLeaseName);
        if (lease.isPresent()) {
            leaseName.set(boundLeaseName);
            reacquireAttempts.set(0);
            leaseEvents.fire(new MemberLeaseEvent(
                    MemberLeaseEvent.Type.ACQUIRED,
                    poolConfig.name(),
                    kubeInfo.podName(),
                    boundLeaseName));
            return true;
        }

        int attempts = reacquireAttempts.incrementAndGet();
        int max = leaseConfig.member().maxReacquireAttempts();
        LOG.warn("Flow: Failed to re-acquire bound lease '{}' (attempt {}/{})",
                boundLeaseName, attempts, max);

        if (attempts >= max) {
            LOG.error("Flow: Bound lease '{}' is permanently lost after {} attempts. "
                    + "The applicationId cannot be recovered — initiating graceful shutdown to avoid split-brain.",
                    boundLeaseName, attempts);
            Quarkus.asyncExit(1);
        }

        return false;
    }

    @Override
    protected void released() {
        leaseEvents.fire(new MemberLeaseEvent(
                MemberLeaseEvent.Type.RELEASED,
                poolConfig.name(),
                kubeInfo.podName(),
                leaseName.get()));
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
