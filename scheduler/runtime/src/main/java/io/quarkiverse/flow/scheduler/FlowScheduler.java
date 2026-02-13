package io.quarkiverse.flow.scheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import io.quarkus.arc.Arc;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Scheduler.JobDefinition;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.scheduler.Cancellable;
import io.serverlessworkflow.impl.scheduler.ExecutorServiceWorkflowScheduler;
import io.serverlessworkflow.impl.scheduler.ScheduledInstanceRunnable;

@ApplicationScoped
public class FlowScheduler extends ExecutorServiceWorkflowScheduler {

    @Inject
    Scheduler scheduler;

    public FlowScheduler() {
        super(Arc.container().select(ScheduledExecutorService.class, Any.Literal.INSTANCE).get());
    }

    @Override
    public Cancellable scheduleEvery(WorkflowDefinition definition, Duration interval) {
        return scheduleJob(definition, j -> j.setInterval(interval.toString()));
    }

    @Override
    public Cancellable scheduleCron(WorkflowDefinition definition, String cron) {
        return scheduleJob(definition, j -> j.setCron(cron));
    }

    @SuppressWarnings("rawtypes")
    private Cancellable scheduleJob(WorkflowDefinition definition, Consumer<JobDefinition> setup) {
        String id = jobId(definition);
        JobDefinition job = scheduler.newJob(id).setTask(new FlowRunnable(definition));
        setup.accept(job);
        job.schedule();
        return () -> scheduler.unscheduleJob(id);
    }

    protected String jobId(WorkflowDefinition definition) {
        return definition.application().idFactory().get();
    }

    private class FlowRunnable implements Consumer<ScheduledExecution> {
        private Runnable runner;

        protected FlowRunnable(WorkflowDefinition definition) {
            this.runner = new ScheduledInstanceRunnable(definition);
        }

        @Override
        public void accept(ScheduledExecution t) {
            runner.run();
        }
    }
}
