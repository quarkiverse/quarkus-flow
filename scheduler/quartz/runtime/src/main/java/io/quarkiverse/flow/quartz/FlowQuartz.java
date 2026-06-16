package io.quarkiverse.flow.quartz;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.scheduler.Cancellable;
import io.serverlessworkflow.impl.scheduler.EventWorkflowScheduler;
import io.serverlessworkflow.impl.scheduler.ScheduledInstanceRunnable;

@ApplicationScoped
public class FlowQuartz extends EventWorkflowScheduler {

    private static final String DEFINITION_VERSION = "definitionVersion";
    private static final String DEFINITION_NAME = "definitionName";
    private static final String DEFINITION_NAMESPACE = "definitionNamespace";

    private final static Logger logger = LoggerFactory.getLogger(FlowQuartz.class);

    @Inject
    Scheduler scheduler;

    public static class QuartzJob implements Job {
        @Inject
        WorkflowApplication app;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            WorkflowDefinitionId id = from(context.getJobDetail().getJobDataMap());
            logger.debug("Quartz is running scheduled instance for workflow definition {}", id);
            ScheduledInstanceRunnable.runScheduledInstance(app.workflowDefinitions().get(id), app.modelFactory().fromNull());
        }
    }

    @Override
    public Cancellable scheduleEvery(WorkflowDefinition definition, Duration interval) {
        return scheduleJob(definition, j -> j.startNow().withSchedule(
                SimpleScheduleBuilder.simpleSchedule().repeatForever().withIntervalInMilliseconds(interval.toMillis())));
    }

    @Override
    public Cancellable scheduleCron(WorkflowDefinition definition, String cron) {
        return scheduleJob(definition, j -> j.startNow().withSchedule(CronScheduleBuilder.cronSchedule(cron)));
    }

    @Override
    public Cancellable scheduleAfter(WorkflowDefinition definition, Duration delay) {
        return scheduleJob(definition, j -> j.startAt(Instant.now().plus(delay)));
    }

    private Cancellable scheduleJob(WorkflowDefinition definition, Consumer<TriggerBuilder<Trigger>> setup) {
        JobDetail job = JobBuilder.newJob(QuartzJob.class).usingJobData(DEFINITION_NAMESPACE, definition.id().namespace())
                .usingJobData(DEFINITION_NAME, definition.id().name())
                .usingJobData(DEFINITION_VERSION, definition.id().version()).build();
        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger();
        setup.accept(triggerBuilder);
        Trigger trigger = triggerBuilder.build();
        try {
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Error scheduling workflow definition " + definition.id(), e);
        }
        return () -> {
            try {
                scheduler.unscheduleJob(trigger.getKey());
            } catch (SchedulerException e) {
                logger.error("Error unscheduling job with key {} for definition {}", trigger.getKey(),
                        definition.id(),
                        e);
            }
        };
    }

    private static WorkflowDefinitionId from(JobDataMap data) {
        return new WorkflowDefinitionId(data.getString(DEFINITION_NAMESPACE),
                data.getString(DEFINITION_NAME),
                data.getString(DEFINITION_VERSION));
    }
}
