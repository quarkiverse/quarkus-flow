package io.quarkiverse.flow.scheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Scheduler.JobDefinition;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.scheduler.Cancellable;
import io.serverlessworkflow.impl.scheduler.EventWorkflowScheduler;
import io.serverlessworkflow.impl.scheduler.ExecutorServiceWorkflowScheduler;
import io.serverlessworkflow.impl.scheduler.ScheduledInstanceRunnable;

@ApplicationScoped
public class FlowScheduler extends EventWorkflowScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowScheduler.class.getName());

    @Inject
    Scheduler scheduler;

    @Inject
    ScheduledExecutorService service;

    @ConfigProperty(name = "quarkus.scheduler.enabled", defaultValue = "true")
    Boolean schedulerEnabled;

    @Override
    public Cancellable scheduleEvery(WorkflowDefinition definition, Duration interval) {
        return scheduleJob(definition, j -> j.setInterval(interval.toString()));
    }

    @Override
    public Cancellable scheduleCron(WorkflowDefinition definition, String cron) {
        return scheduleJob(definition, j -> j.setCron(cron));
    }

    @Override
    public Cancellable scheduleAfter(WorkflowDefinition definition, Duration delay) {
        return ExecutorServiceWorkflowScheduler.scheduleAfter(service, definition, delay);
    }

    @SuppressWarnings("rawtypes")
    private Cancellable scheduleJob(WorkflowDefinition definition, Consumer<JobDefinition> setup) {
        if (!schedulerEnabled) {
            LOGGER.debug("Scheduler is disabled (quarkus.scheduler.enabled), skipping schedule of workflow {}",
                    definition.id());
            return () -> {
            };
        }

        String id = jobId(definition);
        JobDefinition job = scheduler.newJob(id).setTask(t -> ScheduledInstanceRunnable.runScheduledInstance(definition,
                definition.application().modelFactory().fromNull()));
        setup.accept(job);
        job.schedule();
        return () -> scheduler.unscheduleJob(id);
    }

    protected String jobId(WorkflowDefinition definition) {
        return definition.application().idFactory().get();
    }
}
