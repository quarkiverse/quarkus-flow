package io.quarkiverse.flow.durable.kube;

import jakarta.inject.Singleton;

import io.quarkus.arc.Unremovable;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;

@Singleton
@Unremovable
public class PoolMemberController implements Scheduled.SkipPredicate, Runnable {

    @Override
    public boolean test(ScheduledExecution execution) {
        return false;
    }

    @Override
    public void run() {

    }
}
