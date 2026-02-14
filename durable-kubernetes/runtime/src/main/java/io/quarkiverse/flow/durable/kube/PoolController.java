package io.quarkiverse.flow.durable.kube;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;

public abstract class PoolController implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(PoolController.class);
    protected ExecutorService executorService;

    @Inject
    Scheduler scheduler;

    @Inject
    LeaseService leaseService;

    @Inject
    KubeInfoStrategy kubeInfo;

    protected String computeSchedulerDelay() {
        String schedulerInitialDelay = schedulerConfig().initialDelay();
        if (SchedulerGroupConfig.SCHEDULER_INITIAL_DELAY_DEFAULT.equals(schedulerInitialDelay)) {
            schedulerInitialDelay = String.format("%ds", ThreadLocalRandom.current().nextInt(5, 11));
        } else {
            schedulerInitialDelay = String.format("%ss", schedulerInitialDelay);
        }
        return schedulerInitialDelay;
    }

    @PostConstruct
    void init() {
        if (!leaseConfig().enabled()) {
            return;
        }
        String scheduledExecutorName = scheduledExecutorName();
        LOG.info("Flow: Initializing pool controller '{}' scheduler", scheduledExecutorName);
        if (!leaseConfig().enabled()) {
            LOG.info("Flow: Lease Scheduler '{}' disabled", scheduledExecutorName);
            return;
        }
        executorService = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
        String delayed = computeSchedulerDelay();

        LOG.debug("Setting delayed controller '{}' executor to {}", scheduledExecutorName, delayed);

        scheduler.newJob(scheduledExecutorName)
                .setInterval(schedulerConfig().interval())
                .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                .setDelayed(delayed) // in a cluster, where all pods can start at once, we avoid pilling up requests to get the leader lease
                .setTask(executionContext -> executorService.execute(this))
                .schedule();
        LOG.info("Flow: Scheduler '{}' initialized", scheduledExecutorName);
    }

    @PreDestroy
    public void release() {
        try {
            scheduler.unscheduleJob(scheduledExecutorName());
            if (executorService != null) {
                executorService.shutdown();
            }

            final String lease = leaseName();
            final String podName = kubeInfo.podName();
            if (lease != null) {
                if (leaseService.releaseLease(podName, lease)) {
                    LOG.debug("Lease {} has been released from pod {}", lease, podName);
                    this.afterRelease(true);
                }
            }
        } catch (Exception e) {
            LOG.debug("Skipping lease {} release during shutdown: ", leaseName(), e);
        }
        this.afterRelease(false);
    }

    protected void afterRelease(boolean released) {
        // hook for child classes
    }

    protected abstract String scheduledExecutorName();

    protected abstract String leaseName();

    protected abstract SchedulerGroupConfig.SchedulerConfig schedulerConfig();

    protected abstract LeaseGroupConfig.LeaseConfig leaseConfig();

}
