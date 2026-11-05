package io.quarkiverse.flow.durable.kube;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.durable.kube.config.LeaseGroupConfig;
import io.quarkiverse.flow.durable.kube.config.SchedulerGroupConfig;
import io.quarkus.runtime.configuration.DurationConverter;

public abstract class PoolController implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(PoolController.class);
    protected ScheduledExecutorService executorService;

    @Inject
    LeaseService leaseService;

    @Inject
    KubeInfoStrategy kubeInfo;

    @Inject
    LocalStrategy localStrategy;

    protected Duration computeSchedulerDelay() {
        String schedulerInitialDelay = schedulerConfig().initialDelay();
        if (SchedulerGroupConfig.SCHEDULER_INITIAL_DELAY_DEFAULT.equals(schedulerInitialDelay)) {
            return Duration.ofSeconds(ThreadLocalRandom.current().nextInt(5, 11));
        }
        if (schedulerInitialDelay.matches("\\d+")) {
            return Duration.ofSeconds(Long.parseLong(schedulerInitialDelay));
        }
        return DurationConverter.parseDuration(schedulerInitialDelay);
    }

    protected void onLeaseStartup(@Observes LeaseStartupEvent ev) {
        if (!leaseConfig().enabled()) {
            return;
        }
        String scheduledExecutorName = scheduledExecutorName();
        if (localStrategy.enabled()) {
            LOG.debug("Flow: pool controller '{}' disabled by local mode strategy", scheduledExecutorName);
            return;
        }
        LOG.info("Flow: Initializing pool controller '{}' scheduler", scheduledExecutorName);
        executorService = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "flow-pool-controller-" + scheduledExecutorName());
            thread.setDaemon(true);
            return thread;
        });
        Duration delayed = computeSchedulerDelay();

        LOG.debug("Setting delayed controller '{}' executor to {}", scheduledExecutorName, delayed);

        long intervalMillis = schedulerConfig().interval().toMillis();
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Pool controller interval must be greater than zero");
        }
        executorService.scheduleWithFixedDelay(this, delayed.toMillis(), intervalMillis, TimeUnit.MILLISECONDS);
        LOG.info("Flow: Scheduler '{}' initialized", scheduledExecutorName);
    }

    @PreDestroy
    public void release() {
        if (localStrategy.enabled()) {
            return;
        }

        try {
            if (executorService != null) {
                executorService.shutdownNow();
                executorService = null;
            }

            final String lease = leaseName();
            final String podName = kubeInfo.podName();
            if (lease != null) {
                if (leaseService.releaseLease(podName, lease)) {
                    LOG.debug("Lease {} has been released from pod {}", lease, podName);
                    this.released();
                }
            }
        } catch (Exception e) {
            LOG.debug("Skipping lease {} release during shutdown: ", leaseName(), e);
        }
    }

    protected void released() {
        // hook for child classes
    }

    protected abstract String scheduledExecutorName();

    protected abstract String leaseName();

    protected abstract SchedulerGroupConfig.SchedulerConfig schedulerConfig();

    protected abstract LeaseGroupConfig.LeaseConfig leaseConfig();

}
