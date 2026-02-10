package io.quarkiverse.flow.durable.kube;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.quarkus.runtime.Startup;

/**
 * Runs on every pod on a given interval scheduled via property configuration (check the Quarkus Flow Durable Workflows docs).
 * <p/>
 * Every 30 seconds (by default) checks whether this instance is the leader.
 * If true, guarantees that every pod instance in this deployment has a Lease to start accepting instance requests.
 */
@Startup
@Singleton
public class PoolLeaderController extends PoolController {

    private static final String POOL_LEADER_NAME_FMT = "flow-pool-leader-%s";
    private static final String POOL_MEMBER_NAME_FMT = "flow-pool-member-%s-%02d";
    private static final String POOL_LEADER_SCHEDULER_FMT = "flow-pool-leader-scheduler-%s";
    private static final Logger LOG = LoggerFactory.getLogger(PoolLeaderController.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Inject
    FlowDurableKubeSettings settings;

    @Inject
    PoolConfig poolConfig;

    @Inject
    KubeInfoStrategy kubeInfo;

    @Override
    public void run() {
        LOG.debug("Attempt to run pool leader controller scheduler");
        if (!settings.pool().leader().leaseEnabled())
            return;

        if (!running.compareAndSet(false, true))
            return;

        try {
            if (!reconcile() && LOG.isDebugEnabled())
                LOG.debug("Pod {} is not the leader, skipping reconcile", kubeInfo.podName());
        } catch (Exception e) {
            LOG.warn("Leader reconcile failed on pod {}", kubeInfo.podName(), e);
        } finally {
            running.set(false);
        }
    }

    /**
     * Creates or update all the lease objects for the current pool and renew this leader lease.
     */
    boolean reconcile() {
        final String poolLeaderName = leaseName();

        if (!leaseService.tryAcquireLeaderLease(kubeInfo.podName(), poolLeaderName)) {
            LOG.debug("Failed to acquire leader lease for pool {} on pod {}", poolLeaderName, kubeInfo.podName());
            return false;
        }

        LOG.debug("Pool leader reconciliation running for lease '{}'", poolLeaderName);

        final Optional<Integer> replicas = leaseService.desiredReplicas();
        if (replicas.isPresent()) {
            final String poolName = settings.controllers().poolName();
            for (int i = 0; i < replicas.get(); i++) {
                final String poolMemberName = String.format(POOL_MEMBER_NAME_FMT, poolName, i);
                final Optional<Lease> leaseMember = leaseService.createOrUpdateMemberLease(poolMemberName);
                if (leaseMember.isEmpty()) {
                    LOG.warn("Failed to reconcile lease {} for pool {}", poolMemberName, poolName);
                } else {
                    LOG.debug("Lease {} has been reconciled for pool {}", leaseMember.get().getMetadata().getName(), poolName);
                }
            }
        }

        return true;
    }

    @Override
    protected String scheduledExecutorName() {
        return String.format(POOL_LEADER_SCHEDULER_FMT, settings.controllers().poolName());
    }

    @Override
    protected String leaseName() {
        return String.format(POOL_LEADER_NAME_FMT, settings.controllers().poolName());
    }

    @Override
    protected ControllersConfig.SchedulerConfig schedulerConfig() {
        return settings.controllers().leader();
    }

    @Override
    protected PoolConfig.LeaseConfig leaseConfig() {
        return poolConfig.leader();
    }
}
