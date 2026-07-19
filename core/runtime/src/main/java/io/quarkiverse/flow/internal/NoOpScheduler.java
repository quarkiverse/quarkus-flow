package io.quarkiverse.flow.internal;

import java.time.Duration;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.scheduler.Cancellable;
import io.serverlessworkflow.impl.scheduler.EventWorkflowScheduler;

@Singleton
public class NoOpScheduler extends EventWorkflowScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpScheduler.class);

    @Override
    public Cancellable scheduleEvery(WorkflowDefinition definition, Duration interval) {
        LOG.debug("Schedule every operation ignored");
        return () -> {
        };
    }

    @Override
    public Cancellable scheduleAfter(WorkflowDefinition definition, Duration delay) {
        LOG.debug("Schedule after operation ignored");
        return () -> {
        };
    }

    @Override
    public Cancellable scheduleCron(WorkflowDefinition definition, String cron) {
        LOG.debug("Schedule cron operation ignored");
        return () -> {
        };
    }

}
