package io.quarkiverse.flow.durable.kube;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;

/**
 * Runs on every pod on a given interval scheduled via property configuration (check the Quarkus Flow Durable Workflows docs).
 * <p/>
 * Every 30 seconds (by default) checks whether this instance is the leader.
 * If true, guarantees that every pod instance in this deployment has a Lease to start accepting instance requests.
 */
@Startup
@Unremovable
public class PoolLeaderController implements Runnable {

    private static final String POOL_LEADER_NAME_FMT = "flow-pool-leader-%s";
    private static final String POOL_MEMBER_NAME_FMT = "flow-pool-member-%s-%02d";
    private static final String POOL_LEADER_SCHEDULER_FMT = "flow-pool-leader-scheduler-%s";
    private static final Logger LOG = LoggerFactory.getLogger(PoolLeaderController.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Inject
    LeaseService leaseService;
    @Inject
    FlowDurableKubeSettings config;
    @Inject
    KubeInfoStrategy kubeInfo;
    @Inject
    Scheduler scheduler;

    private ExecutorService executorService;
    private String scheduledExecutorName;

    @PostConstruct
    void init() {
        LOG.info("Flow: Initializing pool leader controller scheduler");
        if (!config.pool().leader().leaseEnabled()) {
            LOG.info("Flow: Lease Leader Scheduler disabled");
            return;
        }
        executorService = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
        scheduledExecutorName = String.format(POOL_LEADER_SCHEDULER_FMT, config.controllers().poolName());
        String delayed = config.controllers().leader().schedulerInitialDelay();
        if (ControllersConfig.SCHEDULER_INITIAL_DELAY_DEFAULT.equals(delayed)) {
            delayed = String.format("%ds", ThreadLocalRandom.current().nextInt(5, 11));
        } else {
            delayed = String.format("%ss", delayed);
        }

        LOG.debug("Setting delayed leader controller executor to {}", delayed);

        scheduler.newJob(scheduledExecutorName)
                .setInterval(config.controllers().leader().schedulerInterval())
                .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                .setDelayed(delayed) // in a cluster, where all pods can start at once, we avoid pilling up requests to get the leader lease
                .setTask(executionContext -> executorService.execute(this))
                .schedule();
        LOG.info("Flow: Leader Scheduler '{}' initialized", scheduledExecutorName);
    }

    @PreDestroy
    public void release() {
        scheduler.unscheduleJob(scheduledExecutorName);
        if (executorService != null) {
            executorService.shutdown();
        }
        if (leaseService.releaseLease(kubeInfo.podName(), poolLeaderLeaseName())) {
            LOG.debug("Lease {} has been released from pod {}", poolLeaderLeaseName(), kubeInfo.podName());
        }
    }

    @Override
    public void run() {
        LOG.debug("Attempt to run pool leader controller scheduler");
        if (!config.pool().leader().leaseEnabled())
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
        final String poolLeaderName = poolLeaderLeaseName();

        if (!leaseService.tryAcquireLeaderLease(kubeInfo.podName(), poolLeaderName)) {
            return false;
        }

        LOG.debug("Pool leader reconciliation running on pool '{}'", poolLeaderName);

        final Optional<Integer> replicas = leaseService.desiredReplicas();
        if (replicas.isPresent()) {
            final String poolName = config.controllers().poolName();
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

    private String poolLeaderLeaseName() {
        return String.format(POOL_LEADER_NAME_FMT, config.controllers().poolName());
    }
}
